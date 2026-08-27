package uz.safecity.transportobserver.inspections.service

import uz.safecity.transportobserver.audit.service.AuditService
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import uz.safecity.transportobserver.checkpoints.repository.CheckpointRepository
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.common.util.StatusTransitionValidator
import uz.safecity.transportobserver.inspections.dto.ChecklistItemRequest
import uz.safecity.transportobserver.inspections.dto.CreateInspectionRequest
import uz.safecity.transportobserver.inspections.dto.InspectionDetailDto
import uz.safecity.transportobserver.inspections.dto.InspectionDto
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionChecklistItem
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.entity.InspectionStatusEvent
import uz.safecity.transportobserver.inspections.repository.InspectionChecklistItemRepository
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.inspections.repository.InspectionStatusEventRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Planned, admin/operator-assigned inspection tasks against a [Checkpoint]
 * (TZ section 6) — see [uz.safecity.transportobserver.inspections.entity.Inspection]
 * kdoc for how this differs from [uz.safecity.transportobserver.incidents.entity.Incident].
 *
 * INSPECTOR scoping mirrors [uz.safecity.transportobserver.incidents.service.IncidentService]
 * throughout this file: every read/write for an INSPECTOR caller is scoped
 * server-side to `Inspection.assignedInspectorId == principal.accountId` —
 * never trust a frontend to hide the rest, since the same JWT can call the
 * API directly. SUPER_ADMIN/ADMIN/OPERATOR see and manage everything.
 */
@Service
class InspectionService(
	private val inspectionRepository: InspectionRepository,
	private val checkpointRepository: CheckpointRepository,
	private val accountRepository: AccountRepository,
	private val auditService: AuditService,
	private val checklistItemRepository: InspectionChecklistItemRepository,
	private val statusEventRepository: InspectionStatusEventRepository
) {

	/**
	 * Admin/operator board + inspector's own task list: filter by [status]/[checkpointId]/
	 * [assignedInspectorId] with pagination. [assignedInspectorId] is only meaningful for
	 * SUPER_ADMIN/ADMIN/OPERATOR callers — for an INSPECTOR principal it is ignored and the
	 * INSPECTOR-scoping predicate from [buildSpecification] wins instead, so an inspector can
	 * never widen their own view by passing (or omitting) that param. Mirrors
	 * IncidentService#list / #buildSpecification exactly, including why scoping lives in the
	 * same [Specification] as the filters rather than being applied afterwards (keeps
	 * pagination totals correct).
	 */
	fun list(
		status: InspectionStatus?,
		checkpointId: UUID?,
		assignedInspectorId: UUID?,
		principal: CustomUserDetails,
		pageable: Pageable
	): PageResponse<InspectionDto> {
		val page = inspectionRepository.findAll(
			buildSpecification(status, checkpointId, assignedInspectorId, principal),
			pageable
		)
		val checkpointsById = checkpointsById(page.content)
		return PageResponse(
			content = page.content.map { InspectionDto.from(it, checkpointsById[it.checkpointId]) },
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages
		)
	}

	/**
	 * `GET /inspections/{id}` — the mobile/web "Tekshiruv hisoboti" detail screen. Returns
	 * [InspectionDetailDto] (checklist + signatures + JARAYON timeline), unlike [list]'s plain
	 * [InspectionDto] — see that DTO's kdoc for why the two shapes are kept separate.
	 */
	fun getById(id: UUID, principal: CustomUserDetails): InspectionDetailDto {
		val inspection = if (principal.role == RoleType.INSPECTOR) {
			// Ownership check baked into the query itself (see repository kdoc): a foreign
			// inspection id returns empty here, so it surfaces as 404, never as "found but
			// forbidden" — that would leak that the id exists.
			inspectionRepository.findByIdAndAssignedInspectorId(id, principal.accountId)
		} else {
			inspectionRepository.findById(id)
		}.orElseThrow { ResourceNotFoundException("error.inspection.not-found", id) }

		val checkpoint = checkpointRepository.findById(inspection.checkpointId).orElse(null)
		val checklistItems = checklistItemRepository.findByInspectionIdOrderByOrderIndexAsc(id)
		val statusHistory = statusEventRepository.findByInspectionIdOrderByOccurredAtAsc(id)
		return InspectionDetailDto.from(inspection, checkpoint, checklistItems, statusHistory)
	}

	/**
	 * SUPER_ADMIN/ADMIN/OPERATOR only. Primarily enforced by @PreAuthorize on the controller,
	 * but [actorRole] is re-checked here too (defense-in-depth — same pattern as
	 * IncidentService.assignInspector / EmployeeService.assertCanManageRole) so this method stays
	 * safe to call from anywhere else in the codebase later without silently reopening a
	 * privilege-escalation gap. Also validates [CreateInspectionRequest.checkpointId] exists and,
	 * if [CreateInspectionRequest.assignedInspectorId] is given, that it belongs to an active
	 * INSPECTOR account.
	 */
	@Transactional
	fun create(request: CreateInspectionRequest, actorAccountId: UUID?, actorRole: RoleType): InspectionDto {
		assertCanManage(actorRole)

		val checkpointId = requireNotNull(request.checkpointId)
		val checkpoint = checkpointRepository.findById(checkpointId)
			.orElseThrow { BadRequestException("error.inspection.checkpoint-not-found", checkpointId) }

		request.assignedInspectorId?.let { assertActiveInspector(it) }

		val inspection = Inspection(
			checkpointId = checkpointId,
			assignedInspectorId = request.assignedInspectorId,
			scheduledAt = request.scheduledAt,
			notes = request.notes,
			createdBy = actorAccountId
		)
		val saved = inspectionRepository.save(inspection)

		auditService.record(
			actorAccountId = actorAccountId,
			action = "INSPECTION_CREATED",
			entityType = "Inspection",
			entityId = saved.id,
			metadata = "checkpointId=$checkpointId,assignedInspectorId=${request.assignedInspectorId}"
		)

		return InspectionDto.from(saved, checkpoint)
	}

	/**
	 * SUPER_ADMIN/ADMIN/OPERATOR may change any inspection's status. INSPECTOR may change status
	 * only on an inspection assigned to itself — a field inspector marking their own task
	 * "boshlandi"/"yakunlandi". The lookup reuses the same `findByIdAndAssignedInspectorId`
	 * ownership-scoped query as [getById] so a foreign inspection id surfaces as 404 for an
	 * INSPECTOR caller, never 403 — mirrors IncidentService#updateStatus exactly.
	 *
	 * When [status] is [InspectionStatus.COMPLETED] and [Inspection.performedAt] is not already
	 * set, it is auto-filled with the current time — the caller does not need to (and normally
	 * cannot know to) pass it explicitly.
	 *
	 * Status transitions follow [STATUS_TRANSITIONS] (see that constant's kdoc for the graph).
	 * SUPER_ADMIN/ADMIN bypass it entirely — see [assertValidTransition] kdoc — OPERATOR/INSPECTOR
	 * are held to it strictly, mirroring [uz.safecity.transportobserver.incidents.service.IncidentService.assertValidTransition].
	 *
	 * ENG ODDIY contract for the "Tekshiruv hisoboti" detail screen (`TO-Screen.dc.html`
	 * `reportDetail`): there is no separate `/complete` endpoint and no server-side checklist
	 * template. This SAME endpoint doubles as the completion call — the mobile client (which
	 * already knows the checklist's band names) submits the full "BANDLAR NATIJASI" list via
	 * [checklistItems], plus the "TASDIQ VA IMZOLAR" signals ([driverConfirmed]/[witnessName]),
	 * together with `status = COMPLETED`. All three are ignored for any other target [status] —
	 * see [uz.safecity.transportobserver.inspections.dto.UpdateInspectionStatusRequest] kdoc.
	 * This also writes the "JARAYON" timeline ([InspectionStatusEvent]):
	 * - PLANNED -> IN_PROGRESS writes "Tekshiruv boshlandi".
	 * - -> COMPLETED writes, in order: "Bandlar to'ldirildi" (only if [checklistItems] is
	 *   non-empty), "Imzolar olindi" (only if this call newly set the inspector and/or driver
	 *   signature timestamp — see [Inspection.inspectorSignedAt]/[Inspection.driverSignedAt]
	 *   kdoc for what "signed" means here), then always "Markazga yuborildi".
	 */
	@Transactional
	fun updateStatus(
		id: UUID,
		status: InspectionStatus,
		notes: String?,
		actorAccountId: UUID?,
		actorRole: RoleType,
		checklistItems: List<ChecklistItemRequest>? = null,
		driverConfirmed: Boolean = false,
		witnessName: String? = null
	): InspectionDto {
		assertCanUpdateStatus(actorRole)

		val inspection = if (actorRole == RoleType.INSPECTOR) {
			val inspectorAccountId = actorAccountId
				?: throw ForbiddenException("error.inspection.status-change-forbidden")
			inspectionRepository.findByIdAndAssignedInspectorId(id, inspectorAccountId)
				.orElseThrow { ResourceNotFoundException("error.inspection.not-found", id) }
		} else {
			inspectionRepository.findById(id)
				.orElseThrow { ResourceNotFoundException("error.inspection.not-found", id) }
		}

		val previousStatus = inspection.status
		assertValidTransition(previousStatus, status, actorRole)

		inspection.status = status
		notes?.let { inspection.notes = it }
		if (status == InspectionStatus.COMPLETED && inspection.performedAt == null) {
			inspection.performedAt = Instant.now()
		}

		if (previousStatus != InspectionStatus.IN_PROGRESS && status == InspectionStatus.IN_PROGRESS) {
			statusEventRepository.save(InspectionStatusEvent(inspectionId = id, label = "Tekshiruv boshlandi"))
		}

		if (status == InspectionStatus.COMPLETED) {
			completeChecklistAndSignatures(id, inspection, checklistItems, driverConfirmed, witnessName)
		}

		val saved = inspectionRepository.save(inspection)

		auditService.record(
			actorAccountId = actorAccountId,
			action = "INSPECTION_STATUS_CHANGED",
			entityType = "Inspection",
			entityId = id,
			metadata = "status=$status"
		)

		val checkpoint = checkpointRepository.findById(saved.checkpointId).orElse(null)
		return InspectionDto.from(saved, checkpoint)
	}

	/**
	 * The "checklist + signatures + JARAYON" half of completing an inspection — split out of
	 * [updateStatus] purely to keep that method's already-long kdoc/state-machine logic readable.
	 * Only ever called when [status] is being set to [InspectionStatus.COMPLETED]; see
	 * [updateStatus] kdoc for the full ordering contract.
	 */
	private fun completeChecklistAndSignatures(
		id: UUID,
		inspection: Inspection,
		checklistItems: List<ChecklistItemRequest>?,
		driverConfirmed: Boolean,
		witnessName: String?
	) {
		if (!checklistItems.isNullOrEmpty()) {
			// Wholesale replace — see InspectionChecklistItem kdoc for why this isn't a diff/upsert.
			checklistItemRepository.deleteByInspectionId(id)
			checklistItemRepository.saveAll(
				checklistItems.mapIndexed { index, item ->
					InspectionChecklistItem(
						inspectionId = id,
						label = requireNotNull(item.label),
						result = requireNotNull(item.result),
						deficiencyNote = item.deficiencyNote,
						orderIndex = index
					)
				}
			)
			statusEventRepository.save(InspectionStatusEvent(inspectionId = id, label = "Bandlar to'ldirildi"))
		}

		var signedThisCall = false
		if (inspection.inspectorSignedAt == null) {
			inspection.inspectorSignedAt = Instant.now()
			signedThisCall = true
		}
		if (driverConfirmed && inspection.driverSignedAt == null) {
			inspection.driverSignedAt = Instant.now()
			signedThisCall = true
		}
		if (!witnessName.isNullOrBlank()) {
			inspection.witnessName = witnessName
		}
		if (signedThisCall) {
			statusEventRepository.save(InspectionStatusEvent(inspectionId = id, label = "Imzolar olindi"))
		}

		statusEventRepository.save(InspectionStatusEvent(inspectionId = id, label = "Markazga yuborildi"))
	}

	/** Defense-in-depth mirror of the controller's @PreAuthorize on `POST /inspections`. */
	private fun assertCanManage(actorRole: RoleType) {
		val allowed = setOf(RoleType.SUPER_ADMIN, RoleType.ADMIN, RoleType.OPERATOR)
		if (actorRole !in allowed) {
			throw ForbiddenException("error.inspection.create-forbidden")
		}
	}

	/**
	 * Defense-in-depth mirror of the controller's @PreAuthorize on `PATCH /inspections/{id}/status`
	 * — kept even though it currently admits every RoleType, so a future role added to the enum
	 * doesn't silently gain this capability just by reaching this method directly (same rationale
	 * as IncidentService.assertCanUpdateStatus).
	 */
	private fun assertCanUpdateStatus(actorRole: RoleType) {
		val allowed = setOf(RoleType.SUPER_ADMIN, RoleType.ADMIN, RoleType.OPERATOR, RoleType.INSPECTOR)
		if (actorRole !in allowed) {
			throw ForbiddenException("error.inspection.status-change-forbidden")
		}
	}

	/**
	 * SUPER_ADMIN/ADMIN bypass [STATUS_TRANSITIONS] entirely — same "fix a mistake" escape hatch as
	 * [uz.safecity.transportobserver.incidents.service.IncidentService.assertValidTransition].
	 * OPERATOR/INSPECTOR are held to the graph strictly.
	 */
	private fun assertValidTransition(current: InspectionStatus, target: InspectionStatus, actorRole: RoleType) {
		val bypass = actorRole == RoleType.SUPER_ADMIN || actorRole == RoleType.ADMIN
		StatusTransitionValidator.assertAllowed(
			current = current,
			target = target,
			allowedTransitions = STATUS_TRANSITIONS,
			wildcardTargets = setOf(InspectionStatus.CANCELLED),
			bypass = bypass
		) {
			throw BadRequestException("error.inspection.invalid-status-transition", current, target)
		}
	}

	private fun assertActiveInspector(inspectorAccountId: UUID) {
		val account = accountRepository.findById(inspectorAccountId)
			.orElseThrow { BadRequestException("error.inspection.assign-inspector-account-not-found", inspectorAccountId) }
		if (account.role != RoleType.INSPECTOR) {
			throw BadRequestException("error.inspection.assign-role-invalid")
		}
		if (!account.isActive) {
			throw BadRequestException("error.inspection.assign-inspector-inactive")
		}
	}

	/** Batched lookup so a page of N inspections costs one extra query, not N — see InspectionDto kdoc. */
	private fun checkpointsById(inspections: List<Inspection>): Map<UUID, Checkpoint> =
		checkpointRepository.findAllById(inspections.map { it.checkpointId }).associateBy { requireNotNull(it.id) }

	/**
	 * INSPECTOR scoping is folded into the same [Specification] as the status/checkpointId/
	 * assignedInspectorId filters (rather than filtering the Page afterwards) so pagination
	 * totals stay correct and an INSPECTOR can never end up with a wider result set than their
	 * own assigned inspections no matter what filter params are passed — mirrors
	 * IncidentService#buildSpecification exactly.
	 */
	private fun buildSpecification(
		status: InspectionStatus?,
		checkpointId: UUID?,
		assignedInspectorId: UUID?,
		principal: CustomUserDetails
	): Specification<Inspection> =
		Specification { root, _, cb ->
			val predicates = mutableListOf<Predicate>()
			status?.let { predicates.add(cb.equal(root.get<InspectionStatus>("status"), it)) }
			checkpointId?.let { predicates.add(cb.equal(root.get<UUID>("checkpointId"), it)) }

			if (principal.role == RoleType.INSPECTOR) {
				predicates.add(cb.equal(root.get<UUID>("assignedInspectorId"), principal.accountId))
			} else {
				assignedInspectorId?.let { predicates.add(cb.equal(root.get<UUID>("assignedInspectorId"), it)) }
			}

			cb.and(*predicates.toTypedArray())
		}

	companion object {
		/**
		 * The inspection workflow state machine (OPERATOR/INSPECTOR-enforced — see
		 * [assertValidTransition]): a strictly linear `PLANNED -> IN_PROGRESS -> COMPLETED`, no
		 * backward moves at all (unlike [uz.safecity.transportobserver.incidents.service.IncidentService],
		 * an Inspection is a scheduled task, not a case that gets "reopened" — once performed, it
		 * stays performed). [InspectionStatus.CANCELLED] is deliberately NOT a key/target here — it
		 * is handled as a wildcard target in [assertValidTransition] instead, since "cancel this
		 * task" must be reachable from ANY current status (PLANNED, IN_PROGRESS, and even an
		 * already-COMPLETED inspection an admin/operator later needs to void).
		 */
		private val STATUS_TRANSITIONS: Map<InspectionStatus, Set<InspectionStatus>> = mapOf(
			InspectionStatus.PLANNED to setOf(InspectionStatus.IN_PROGRESS),
			InspectionStatus.IN_PROGRESS to setOf(InspectionStatus.COMPLETED)
		)
	}
}

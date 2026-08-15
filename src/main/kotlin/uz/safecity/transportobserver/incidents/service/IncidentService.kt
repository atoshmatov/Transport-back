package uz.safecity.transportobserver.incidents.service

import uz.safecity.transportobserver.audit.service.AuditService
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.common.util.GeoUtils
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.dto.IncidentDto
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.EvidenceRepository
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * INSPECTOR scoping pattern (TASK: inspector web access): every read is
 * scoped server-side to `Incident.assignedInspectorId == principal.accountId`
 * for INSPECTOR callers — never trust a frontend to hide the rest, since the
 * same JWT can call the API directly. SUPER_ADMIN/ADMIN/OPERATOR see
 * everything, matching the existing EmployeeService role-hierarchy style
 * (see EmployeeService.assertCanManageRole kdoc for the sibling pattern on
 * the employees module).
 *
 * TODO (next phase): status transitions + RabbitMQ event publish (see
 * common.config.RabbitMQConfig) so `notifications` can react to INCIDENT_CREATED /
 * INCIDENT_STATUS_CHANGED.
 */
@Service
class IncidentService(
	private val incidentRepository: IncidentRepository,
	private val evidenceRepository: EvidenceRepository,
	private val accountRepository: AccountRepository,
	private val auditService: AuditService
) {

	/**
	 * Admin/operator board (TZ section 7): filter by [status]/[type]/[assignedInspectorId] with
	 * pagination. [assignedInspectorId] is only meaningful for SUPER_ADMIN/ADMIN/OPERATOR callers —
	 * for an INSPECTOR principal it is ignored and the INSPECTOR-scoping predicate from
	 * [buildSpecification] wins instead, so an inspector can never widen their own view by
	 * passing (or omitting) that param. See [buildSpecification] kdoc for why scoping lives in the
	 * same Specification as the filters rather than being applied afterwards.
	 */
	fun list(
		status: IncidentStatus?,
		type: IncidentType?,
		assignedInspectorId: UUID?,
		principal: CustomUserDetails,
		pageable: Pageable
	): PageResponse<IncidentDto> {
		val page = incidentRepository.findAll(
			buildSpecification(status, type, assignedInspectorId, principal),
			pageable
		)
		// Batched evidence-count lookup (one grouped query for the whole page) instead of one
		// countByIncidentId call per row — see EvidenceCountProjection kdoc.
		val ids = page.content.mapNotNull { it.id }
		val evidenceCountsById = if (ids.isEmpty()) {
			emptyMap()
		} else {
			evidenceRepository.countGroupByIncidentIds(ids).associate { it.incidentId to it.cnt }
		}
		return PageResponse(
			content = page.content.map { IncidentDto.from(it, evidenceCountsById[it.id] ?: 0) },
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages
		)
	}

	fun getById(id: UUID, principal: CustomUserDetails): IncidentDto {
		val incident = if (principal.role == RoleType.INSPECTOR) {
			// Ownership check baked into the query itself (see repository kdoc):
			// a foreign incident id returns empty here, so it surfaces as 404,
			// never as "found but forbidden" — that would leak that the id exists.
			incidentRepository.findByIdAndAssignedInspectorId(id, principal.accountId)
		} else {
			incidentRepository.findById(id)
		}
		return incident.map { toDto(it) }
			.orElseThrow { ResourceNotFoundException("Hodisa topilmadi: $id") }
	}

	/**
	 * Mobile's primary write path (TZ sections 7/8) — the single most important gap this task
	 * closes: until now there was no way for the mobile app to create an incident at all, only
	 * to read/assign/re-status ones that already existed.
	 *
	 * Dedup: if [CreateIncidentRequest.clientUuid] matches an already-created incident (the
	 * mobile app generates this UUID locally, before the request ever leaves the device — TZ
	 * 8.2 "local DB is source of truth, client_uuid for offline dedup"), that existing row is
	 * returned as-is instead of inserting a duplicate. This covers the "device retried an
	 * offline-sync POST after a flaky connection acked the first attempt server-side but the
	 * response never reached the client" case.
	 *
	 * SECURITY: `clientUuid` is NOT treated as an unguessable secret — it is echoed back in
	 * `GET /incidents` responses visible to ADMIN/OPERATOR, so a malicious caller could replay
	 * someone else's `clientUuid` on `POST /incidents` to fish for that incident's full detail
	 * (title/description/location/assignedInspectorId). So the dedup hit is only honored when
	 * the caller owns it (`reportedBy` or `assignedInspectorId` match) or is a
	 * SUPER_ADMIN/ADMIN/OPERATOR — mirrors [getById]'s ownership rule. On a mismatch we do NOT
	 * throw 403/404 (that would confirm the row exists to someone probing it) — we silently fall
	 * through and create a new incident instead, exactly as if no match had been found. A real
	 * `clientUuid` collision between two unrelated devices is practically impossible (client
	 * generates a random UUID), so this fallback path is safe in practice, not just in theory.
	 *
	 * `status` is always [IncidentStatus.NEW] — a client cannot set it directly. `reportedBy` is
	 * always the caller. `assignedInspectorId` auto-assigns to the caller ONLY when they are an
	 * INSPECTOR: they just reported the incident, so it's mechanically simplest and matches "see
	 * my own report immediately" for them to already be its assignee, rather than making them
	 * wait for a SUPER_ADMIN/ADMIN/OPERATOR to run `PATCH /{id}/assign` back to themselves. Any
	 * other caller role (Operator manual entry, per TZ's permission-matrix "qo'lda") leaves it
	 * unassigned — exactly today's default — so it goes through the normal dispatch flow.
	 */
	@Transactional
	fun create(request: CreateIncidentRequest, principal: CustomUserDetails): IncidentDto {
		request.clientUuid?.let { clientUuid ->
			incidentRepository.findByClientUuid(clientUuid)?.let { existing ->
				if (isOwnedByOrVisibleTo(existing, principal)) {
					return toDto(existing)
				}
				// Belongs to someone else and caller has no elevated visibility: treat as if no
				// dedup match existed at all (see kdoc above) — fall through to normal creation.
			}
		}

		val incident = Incident(
			title = request.title,
			description = request.description,
			type = requireNotNull(request.type),
			actionType = request.actionType,
			location = GeoUtils.toPointOrNull(request.latitude, request.longitude),
			reportedBy = principal.accountId,
			occurredAt = request.occurredAt ?: Instant.now(),
			assignedInspectorId = if (principal.role == RoleType.INSPECTOR) principal.accountId else null,
			regionName = null,
			clientUuid = request.clientUuid
		)
		val saved = incidentRepository.save(incident)

		auditService.record(
			actorAccountId = principal.accountId,
			action = "INCIDENT_CREATED",
			entityType = "Incident",
			entityId = saved.id,
			metadata = "type=${saved.type}"
		)

		// Freshly created — never has evidence yet, no need for a query.
		return IncidentDto.from(saved, evidenceCount = 0)
	}

	/**
	 * SUPER_ADMIN/ADMIN/OPERATOR only. Primarily enforced by @PreAuthorize on
	 * the controller, but [actorRole] is re-checked here too (defense-in-depth
	 * — same pattern as EmployeeService.assertCanManageRole) so this method
	 * stays safe to call from anywhere else in the codebase later (a batch
	 * job, another service, a controller that forgets the annotation) without
	 * silently reopening the privilege-escalation gap that TASK-583 fixed in
	 * EmployeeService. Also validates the target account actually is an
	 * active INSPECTOR so a typo/wrong-role id can't silently produce an
	 * incident nobody (or the wrong person) can see.
	 */
	@Transactional
	fun assignInspector(id: UUID, inspectorAccountId: UUID, actorAccountId: UUID?, actorRole: RoleType): IncidentDto {
		assertCanAssignInspector(actorRole)

		val incident = incidentRepository.findById(id)
			.orElseThrow { ResourceNotFoundException("Hodisa topilmadi: $id") }

		val inspectorAccount = accountRepository.findById(inspectorAccountId)
			.orElseThrow { BadRequestException("Inspektor hisobi topilmadi: $inspectorAccountId") }
		if (inspectorAccount.role != RoleType.INSPECTOR) {
			throw BadRequestException("Faqat INSPECTOR rolidagi hisobga tayinlash mumkin")
		}
		if (!inspectorAccount.isActive) {
			throw BadRequestException("Nofaol inspektor hisobiga tayinlab bo'lmaydi")
		}

		incident.assignedInspectorId = inspectorAccountId
		val saved = incidentRepository.save(incident)

		auditService.record(
			actorAccountId = actorAccountId,
			action = "INCIDENT_INSPECTOR_ASSIGNED",
			entityType = "Incident",
			entityId = id,
			metadata = "inspectorAccountId=$inspectorAccountId"
		)

		return toDto(saved)
	}

	/**
	 * Service-level mirror of the controller's @PreAuthorize on
	 * `PATCH /incidents/{id}/assign` — INSPECTOR (and any future role) must
	 * never be able to reassign incidents even if a caller reaches this
	 * method without going through that controller.
	 */
	private fun assertCanAssignInspector(actorRole: RoleType) {
		val allowed = setOf(RoleType.SUPER_ADMIN, RoleType.ADMIN, RoleType.OPERATOR)
		if (actorRole !in allowed) {
			throw ForbiddenException("Inspektor tayinlashga ruxsatingiz yo'q")
		}
	}

	/**
	 * SUPER_ADMIN/ADMIN/OPERATOR may change any incident's status. INSPECTOR may change status
	 * only on an incident assigned to itself — a field inspector marking their own case
	 * "yakunlandi" (resolved). The lookup reuses the same `findByIdAndAssignedInspectorId`
	 * ownership-scoped query as [getById] so a foreign incident id surfaces as 404 for an
	 * INSPECTOR caller, never as 403 — consistent with the rest of this service's rule of not
	 * confirming another inspector's incident even exists (see [getById] kdoc).
	 *
	 * No status-transition graph is enforced yet (e.g. RESOLVED -> NEW is currently allowed) —
	 * see [uz.safecity.transportobserver.incidents.dto.UpdateIncidentStatusRequest] kdoc; this is
	 * a deliberate TODO until the TZ defines the allowed transitions, not an oversight.
	 */
	@Transactional
	fun updateStatus(id: UUID, status: IncidentStatus, actorAccountId: UUID?, actorRole: RoleType): IncidentDto {
		assertCanUpdateStatus(actorRole)

		val incident = if (actorRole == RoleType.INSPECTOR) {
			val inspectorAccountId = actorAccountId
				?: throw ForbiddenException("Hodisa statusini o'zgartirishga ruxsatingiz yo'q")
			incidentRepository.findByIdAndAssignedInspectorId(id, inspectorAccountId)
				.orElseThrow { ResourceNotFoundException("Hodisa topilmadi: $id") }
		} else {
			incidentRepository.findById(id)
				.orElseThrow { ResourceNotFoundException("Hodisa topilmadi: $id") }
		}

		incident.status = status
		val saved = incidentRepository.save(incident)

		auditService.record(
			actorAccountId = actorAccountId,
			action = "INCIDENT_STATUS_CHANGED",
			entityType = "Incident",
			entityId = id,
			metadata = "status=$status"
		)

		return toDto(saved)
	}

	/**
	 * Ownership gate for the `clientUuid` dedup lookup in [create] — see that method's kdoc for
	 * why an unscoped `findByClientUuid` result must never be handed back to just anyone.
	 */
	private fun isOwnedByOrVisibleTo(incident: Incident, principal: CustomUserDetails): Boolean =
		incident.reportedBy == principal.accountId ||
			incident.assignedInspectorId == principal.accountId ||
			principal.role in setOf(RoleType.SUPER_ADMIN, RoleType.ADMIN, RoleType.OPERATOR)

	/** Single-row DTO with an accurate evidence count — see [list] kdoc for why that's a batch query there instead. */
	private fun toDto(incident: Incident): IncidentDto =
		IncidentDto.from(incident, evidenceRepository.countByIncidentId(requireNotNull(incident.id)))

	/**
	 * Defense-in-depth mirror of the controller's @PreAuthorize on
	 * `PATCH /incidents/{id}/status` — kept even though it currently admits every RoleType,
	 * so a future role added to the enum doesn't silently gain this capability just by
	 * reaching this method directly (same rationale as [assertCanAssignInspector]).
	 */
	private fun assertCanUpdateStatus(actorRole: RoleType) {
		val allowed = setOf(RoleType.SUPER_ADMIN, RoleType.ADMIN, RoleType.OPERATOR, RoleType.INSPECTOR)
		if (actorRole !in allowed) {
			throw ForbiddenException("Hodisa statusini o'zgartirishga ruxsatingiz yo'q")
		}
	}

	/**
	 * INSPECTOR scoping is folded into the same [Specification] as the status/type/
	 * assignedInspectorId filters (rather than, say, filtering the Page afterwards) so
	 * pagination totals stay correct and an INSPECTOR can never end up with a wider result
	 * set than their own assigned incidents no matter what filter params are passed —
	 * mirrors [getById]'s "scoping is baked into the query, not layered on top" rule.
	 */
	private fun buildSpecification(
		status: IncidentStatus?,
		type: IncidentType?,
		assignedInspectorId: UUID?,
		principal: CustomUserDetails
	): Specification<Incident> =
		Specification { root, _, cb ->
			val predicates = mutableListOf<Predicate>()
			status?.let { predicates.add(cb.equal(root.get<IncidentStatus>("status"), it)) }
			type?.let { predicates.add(cb.equal(root.get<IncidentType>("type"), it)) }

			if (principal.role == RoleType.INSPECTOR) {
				predicates.add(cb.equal(root.get<UUID>("assignedInspectorId"), principal.accountId))
			} else {
				assignedInspectorId?.let { predicates.add(cb.equal(root.get<UUID>("assignedInspectorId"), it)) }
			}

			cb.and(*predicates.toTypedArray())
		}
}

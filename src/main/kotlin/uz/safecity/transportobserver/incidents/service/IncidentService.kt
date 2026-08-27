package uz.safecity.transportobserver.incidents.service

import uz.safecity.transportobserver.audit.service.AuditService
import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.common.util.GeoUtils
import uz.safecity.transportobserver.common.util.StatusTransitionValidator
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.dto.CreateSosRequest
import uz.safecity.transportobserver.incidents.dto.IncidentDto
import uz.safecity.transportobserver.incidents.dto.SosBroadcastDto
import uz.safecity.transportobserver.incidents.entity.ActionType
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.event.SosCreatedEvent
import uz.safecity.transportobserver.incidents.repository.EvidenceRepository
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.notifications.service.NotificationService
import jakarta.persistence.criteria.Predicate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
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
	private val employeeRepository: EmployeeRepository,
	private val auditService: AuditService,
	private val notificationService: NotificationService,
	private val applicationEventPublisher: ApplicationEventPublisher
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
		// Batched name lookup (one Account query + one Employee query for the whole page) instead
		// of one round-trip per row — same batching rationale as evidenceCountsById above.
		val namesByInspectorId = resolveInspectorNames(page.content.mapNotNull { it.assignedInspectorId })
		return PageResponse(
			content = page.content.map {
				IncidentDto.from(it, evidenceCountsById[it.id] ?: 0, namesByInspectorId[it.assignedInspectorId])
			},
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
			.orElseThrow { ResourceNotFoundException("error.incident.not-found", id) }
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
	 * wait for a SUPER_ADMIN/ADMIN/OPERATOR to run `PATCH /{id}/assign` back to themselves.
	 *
	 * For SUPER_ADMIN/ADMIN/OPERATOR, [CreateIncidentRequest.assignedInspectorId] — if present —
	 * assigns an inspector in this same call (the web "hodisa yaratish" form's one-step path),
	 * validated with the exact same rules as the standalone [assignInspector] (must exist, must be
	 * an active INSPECTOR account — see [validateAssignableInspector]). Omitted/null leaves it
	 * unassigned — exactly today's default — so it goes through the normal dispatch flow via
	 * `PATCH /{id}/assign` later. An INSPECTOR caller's [CreateIncidentRequest.assignedInspectorId]
	 * (if sent at all) is ignored outright: they always auto-assign to themselves, never to a
	 * third party, from the create endpoint.
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

		val assignOnCreateInspectorId = if (principal.role != RoleType.INSPECTOR) {
			request.assignedInspectorId?.also { validateAssignableInspector(it) }
		} else {
			null
		}

		val incident = Incident(
			title = request.title,
			description = request.description,
			type = requireNotNull(request.type),
			actionType = request.actionType,
			location = GeoUtils.toPointOrNull(request.latitude, request.longitude),
			reportedBy = principal.accountId,
			occurredAt = request.occurredAt ?: Instant.now(),
			assignedInspectorId = if (principal.role == RoleType.INSPECTOR) principal.accountId else assignOnCreateInspectorId,
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

		// Notify the assigned inspector — but only when someone ELSE assigned them (assign-on-create
		// by a SUPER_ADMIN/ADMIN/OPERATOR). An INSPECTOR caller always auto-assigns to themselves
		// (see kdoc above), and notifying someone that they assigned themselves is pointless noise.
		assignOnCreateInspectorId?.let {
			notificationService.notifyAssignment(it, requireNotNull(saved.id), saved.title)
		}

		// Freshly created — never has evidence yet, no need for a query.
		return IncidentDto.from(
			saved,
			evidenceCount = 0,
			assignedInspectorName = saved.assignedInspectorId?.let { resolveInspectorNames(listOf(it))[it] }
		)
	}

	/**
	 * `POST /api/v1/inspector/me/sos` — the inspector panic button (INSPECTOR-only, see
	 * [assertInspectorForSos]). Creates an [Incident] with [CreateSosRequest.type] (the mobile SOS
	 * screen's "Holat turi" picker — defaults to [IncidentType.OTHER] for an older client that
	 * doesn't send it yet, see that field's kdoc) + [ActionType.DANGER_REPORTED] +
	 * [IncidentStatus.NEW] — deliberately reusing the existing status enum rather than adding a
	 * dedicated `PENDING_ACK` value (per task scope: "ortiqcha murakkablashtirmaslik" — the
	 * operator workflow of NEW -> IN_PROGRESS already means "somebody needs to act on this", which
	 * is exactly what an unacknowledged SOS needs). [Incident.isSos] is the one flag that marks
	 * this row as a panic-button report rather than an ordinary report of the same [IncidentType]
	 * — see that field's kdoc.
	 *
	 * Dedup: same [CreateSosRequest.clientUuid] mechanism and same ownership-gated
	 * [isOwnedByOrVisibleTo] check as [create]'s dedup (see that method's kdoc for the full
	 * security reasoning) — if a match is found and visible to this caller, the existing row is
	 * returned as-is instead of inserting a duplicate. This is what makes the mobile SOS screen's
	 * network-error retry (1s/2s/4s backoff) safe: a request that succeeded server-side but whose
	 * response was lost to the same timeout no longer creates a second Incident on retry. Optional
	 * — an older mobile build that predates this field sends no `clientUuid` at all, so no dedup is
	 * attempted and every call creates a new Incident, exactly as before this field existed (an SOS
	 * request must never be rejected for a missing field — same reasoning as [CreateSosRequest.type]).
	 * A `clientUuid` clash between an ordinary [CreateIncidentRequest] and an SOS is not a concern
	 * in practice: both are random UUIDs generated independently on the client, and the column's
	 * `unique` constraint (see [Incident.clientUuid] kdoc) already guarantees the two can never
	 * collide in storage regardless.
	 *
	 * Auto-assigns to the caller (same reasoning as [create]'s INSPECTOR branch: they ARE the
	 * emergency, so there's no dispatch step to wait on) and:
	 *  - persists an audit trail entry (`INCIDENT_SOS_CREATED`)
	 *  - creates a [uz.safecity.transportobserver.notifications.entity.NotificationType.SOS]
	 *    row for every active SUPER_ADMIN/ADMIN/OPERATOR account (see
	 *    [NotificationService.notifySosToAdmins])
	 *  - publishes a [SosCreatedEvent] wrapping a [SosBroadcastDto], which
	 *    [uz.safecity.transportobserver.incidents.listener.SosBroadcastListener] pushes over STOMP to
	 *    `/topic/sos` AFTER this transaction commits, so an admin/operator dashboard already
	 *    connected gets it in real time without waiting for a `GET /notifications` poll — this IS
	 *    the point of an SOS (speed). The push is deliberately AFTER_COMMIT rather than a direct
	 *    `simpMessagingTemplate.convertAndSend(...)` call right here: doing it inline, before this
	 *    `@Transactional` method's commit, could push a live SOS alert to the dashboard for an
	 *    incident that a later rollback then makes not exist in the DB at all — see that listener's
	 *    kdoc for the full reasoning.
	 */
	@Transactional
	fun createSos(request: CreateSosRequest, principal: CustomUserDetails): IncidentDto {
		assertInspectorForSos(principal.role)

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
			title = "SOS / Favqulodda signal",
			type = request.type,
			actionType = ActionType.DANGER_REPORTED,
			location = GeoUtils.toPointOrNull(request.latitude, request.longitude),
			reportedBy = principal.accountId,
			occurredAt = Instant.now(),
			assignedInspectorId = principal.accountId,
			clientUuid = request.clientUuid,
			isSos = true
		)
		val saved = incidentRepository.save(incident)

		auditService.record(
			actorAccountId = principal.accountId,
			action = "INCIDENT_SOS_CREATED",
			entityType = "Incident",
			entityId = saved.id,
			metadata = "type=${saved.type}"
		)

		val inspectorName = resolveInspectorNames(listOf(principal.accountId))[principal.accountId]
		notificationService.notifySosToAdmins(inspectorName, requireNotNull(saved.id))

		applicationEventPublisher.publishEvent(
			SosCreatedEvent(
				SosBroadcastDto(
					incidentId = requireNotNull(saved.id),
					inspectorAccountId = principal.accountId,
					inspectorName = inspectorName,
					latitude = saved.location?.y,
					longitude = saved.location?.x,
					incidentType = saved.type,
					createdAt = saved.createdAt ?: Instant.now()
				)
			)
		)

		return IncidentDto.from(saved, evidenceCount = 0, assignedInspectorName = inspectorName)
	}

	/**
	 * `POST /api/v1/inspector/me/sos/{id}/cancel` — lets the inspector who just pressed the panic
	 * button take it back within [SOS_CANCEL_WINDOW] (misclick / false alarm caught immediately).
	 * Scoped to the caller's own SOS the same ownership-first way as [getById]/[updateStatus] (404,
	 * not 403, on a foreign/non-existent id — see those kdocs).
	 *
	 * Deliberately bypasses [STATUS_TRANSITIONS]/[assertValidTransition] entirely rather than
	 * routing through [updateStatus]: NEW -> REJECTED is not a normally allowed transition (see
	 * that map's kdoc — REJECTED is only reachable from IN_PROGRESS), but "I take back my own SOS
	 * within 5 seconds" is a narrower, differently-authorized action than the general operator
	 * status-change workflow, not a graph edge that should be opened up for every NEW incident.
	 */
	@Transactional
	fun cancelSos(id: UUID, principal: CustomUserDetails): IncidentDto {
		assertInspectorForSos(principal.role)

		val incident = incidentRepository.findByIdAndAssignedInspectorId(id, principal.accountId)
			.orElseThrow { ResourceNotFoundException("error.incident.not-found", id) }

		if (!incident.isSos) {
			throw BadRequestException("error.incident.sos-cancel-not-sos")
		}
		if (incident.status != IncidentStatus.NEW) {
			throw ConflictException("error.incident.sos-cancel-already-processed")
		}
		val createdAt = incident.createdAt ?: throw ConflictException("error.incident.sos-cancel-window-expired")
		if (Duration.between(createdAt, Instant.now()) > SOS_CANCEL_WINDOW) {
			throw ConflictException("error.incident.sos-cancel-window-expired")
		}

		incident.status = IncidentStatus.REJECTED
		val saved = incidentRepository.save(incident)

		auditService.record(
			actorAccountId = principal.accountId,
			action = "INCIDENT_SOS_CANCELLED",
			entityType = "Incident",
			entityId = id
		)

		return toDto(saved)
	}

	/** Defense-in-depth mirror of the controller's @PreAuthorize on the `/me/sos...` endpoints. */
	private fun assertInspectorForSos(role: RoleType) {
		if (role != RoleType.INSPECTOR) {
			throw ForbiddenException("error.incident.sos-forbidden")
		}
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
			.orElseThrow { ResourceNotFoundException("error.incident.not-found", id) }

		validateAssignableInspector(inspectorAccountId)

		incident.assignedInspectorId = inspectorAccountId
		val saved = incidentRepository.save(incident)

		auditService.record(
			actorAccountId = actorAccountId,
			action = "INCIDENT_INSPECTOR_ASSIGNED",
			entityType = "Incident",
			entityId = id,
			metadata = "inspectorAccountId=$inspectorAccountId"
		)

		notificationService.notifyAssignment(inspectorAccountId, id, saved.title)

		return toDto(saved)
	}

	/**
	 * Shared validation for both the standalone [assignInspector] (`PATCH /{id}/assign`) and the
	 * assign-on-create path in [create] — an [inspectorAccountId] is only assignable when it
	 * resolves to an existing, active, INSPECTOR-role [Account]. Returns the validated account
	 * (unused by callers today, but avoids a second lookup if a future caller needs it).
	 */
	private fun validateAssignableInspector(inspectorAccountId: UUID): Account {
		val inspectorAccount = accountRepository.findById(inspectorAccountId)
			.orElseThrow { BadRequestException("error.incident.assign-inspector-account-not-found", inspectorAccountId) }
		if (inspectorAccount.role != RoleType.INSPECTOR) {
			throw BadRequestException("error.incident.assign-role-invalid")
		}
		if (!inspectorAccount.isActive) {
			throw BadRequestException("error.incident.assign-inspector-inactive")
		}
		return inspectorAccount
	}

	/**
	 * Resolves [Account.id] -> [uz.safecity.transportobserver.employees.entity.Employee.fullName]
	 * for a batch of `assignedInspectorId` values, in exactly 2 queries total regardless of batch
	 * size (one `findAllById` on Account, one on Employee) — see [IncidentDto.assignedInspectorName]
	 * kdoc for why this hop through Account.employeeId is needed. Missing/legacy accounts (no
	 * linked Employee row) are simply absent from the returned map rather than erroring.
	 */
	private fun resolveInspectorNames(assignedInspectorIds: Collection<UUID>): Map<UUID, String> {
		val ids = assignedInspectorIds.distinct()
		if (ids.isEmpty()) return emptyMap()
		val accounts = accountRepository.findAllById(ids)
		val employeeIds = accounts.mapNotNull { it.employeeId }
		if (employeeIds.isEmpty()) return emptyMap()
		val fullNameByEmployeeId = employeeRepository.findAllById(employeeIds)
			.mapNotNull { employee -> employee.id?.let { it to employee.fullName } }
			.toMap()
		return accounts.mapNotNull { account ->
			val employeeId = account.employeeId ?: return@mapNotNull null
			val fullName = fullNameByEmployeeId[employeeId] ?: return@mapNotNull null
			requireNotNull(account.id) to fullName
		}.toMap()
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
			throw ForbiddenException("error.incident.assign-forbidden")
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
	 * Status transitions follow [STATUS_TRANSITIONS] (see that constant's kdoc for the graph and
	 * why NEW can never be re-entered). SUPER_ADMIN/ADMIN bypass the graph entirely — see
	 * [assertValidTransition] kdoc — everyone else (OPERATOR/INSPECTOR) is held to it strictly.
	 */
	@Transactional
	fun updateStatus(id: UUID, status: IncidentStatus, actorAccountId: UUID?, actorRole: RoleType): IncidentDto {
		assertCanUpdateStatus(actorRole)

		val incident = if (actorRole == RoleType.INSPECTOR) {
			val inspectorAccountId = actorAccountId
				?: throw ForbiddenException("error.incident.status-change-forbidden")
			incidentRepository.findByIdAndAssignedInspectorId(id, inspectorAccountId)
				.orElseThrow { ResourceNotFoundException("error.incident.not-found", id) }
		} else {
			incidentRepository.findById(id)
				.orElseThrow { ResourceNotFoundException("error.incident.not-found", id) }
		}

		assertValidTransition(incident.status, status, actorRole)

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
		IncidentDto.from(
			incident,
			evidenceRepository.countByIncidentId(requireNotNull(incident.id)),
			incident.assignedInspectorId?.let { resolveInspectorNames(listOf(it))[it] }
		)

	/**
	 * Defense-in-depth mirror of the controller's @PreAuthorize on
	 * `PATCH /incidents/{id}/status` — kept even though it currently admits every RoleType,
	 * so a future role added to the enum doesn't silently gain this capability just by
	 * reaching this method directly (same rationale as [assertCanAssignInspector]).
	 */
	private fun assertCanUpdateStatus(actorRole: RoleType) {
		val allowed = setOf(RoleType.SUPER_ADMIN, RoleType.ADMIN, RoleType.OPERATOR, RoleType.INSPECTOR)
		if (actorRole !in allowed) {
			throw ForbiddenException("error.incident.status-change-forbidden")
		}
	}

	/**
	 * SUPER_ADMIN/ADMIN bypass [STATUS_TRANSITIONS] entirely — a deliberate "fix a mistake" escape
	 * hatch (e.g. an operator fat-fingered REJECTED when they meant RESOLVED) that a strict graph
	 * would otherwise make impossible to correct without a DB console. OPERATOR/INSPECTOR — the
	 * two roles that drive the day-to-day workflow — are held to the graph strictly, since they are
	 * exactly the callers a state machine is meant to protect against ("misclick" reopening a
	 * RESOLVED incident straight back to NEW, etc).
	 */
	private fun assertValidTransition(current: IncidentStatus, target: IncidentStatus, actorRole: RoleType) {
		val bypass = actorRole == RoleType.SUPER_ADMIN || actorRole == RoleType.ADMIN
		StatusTransitionValidator.assertAllowed(
			current = current,
			target = target,
			allowedTransitions = STATUS_TRANSITIONS,
			bypass = bypass
		) {
			throw BadRequestException("error.incident.invalid-status-transition", current, target)
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

	companion object {
		/**
		 * The incident workflow state machine (OPERATOR/INSPECTOR-enforced — see
		 * [assertValidTransition]): `NEW -> IN_PROGRESS -> (RESOLVED | REJECTED)`, with RESOLVED/REJECTED
		 * allowed to reopen back to IN_PROGRESS (e.g. a resolved case turns out to need more work).
		 * [IncidentStatus.NEW] is deliberately unreachable as a *target* from anywhere (no entry maps
		 * TO it) — "reopen" always means IN_PROGRESS, never back to the pre-triage NEW state, since NEW
		 * specifically means "nobody has looked at this yet", which is never true once a status change
		 * has happened at all.
		 */
		private val STATUS_TRANSITIONS: Map<IncidentStatus, Set<IncidentStatus>> = mapOf(
			IncidentStatus.NEW to setOf(IncidentStatus.IN_PROGRESS),
			IncidentStatus.IN_PROGRESS to setOf(IncidentStatus.RESOLVED, IncidentStatus.REJECTED),
			IncidentStatus.RESOLVED to setOf(IncidentStatus.IN_PROGRESS),
			IncidentStatus.REJECTED to setOf(IncidentStatus.IN_PROGRESS)
		)

		/** [cancelSos] window — see that method's kdoc. */
		private val SOS_CANCEL_WINDOW: Duration = Duration.ofSeconds(5)
	}
}

package uz.safecity.transportobserver.shifts.service

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.shifts.dto.WorkShiftDto
import uz.safecity.transportobserver.shifts.entity.WorkShift
import uz.safecity.transportobserver.shifts.repository.WorkShiftRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Ish-smena (work shift) check-in/check-out — MVP scope, INSPECTOR-only self-service (see
 * [WorkShift] kdoc for how this differs from the existing online/presence signals). Every
 * `me/...`-style method is scoped to `principal.accountId`, mirroring every other `/me/...`
 * service in this codebase
 * ([uz.safecity.transportobserver.map.service.InspectorLocationService.upsertMyLocation],
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService]) — an inspector can
 * only ever start/end/read their OWN shift, never another inspector's.
 */
@Service
class WorkShiftService(
	private val workShiftRepository: WorkShiftRepository
) {

	/**
	 * `POST /api/v1/inspector/me/shift/start`. Rejects with [ConflictException] if the caller
	 * already has an open shift — see [WorkShift] kdoc re: at most one open shift per inspector.
	 *
	 * Uses [WorkShiftRepository.startIfNoOpenShift] (atomic `INSERT ... ON CONFLICT`) rather than
	 * a find-then-save — a plain `findByInspectorIdAndEndedAtIsNull` + `save` is not safe here:
	 * two concurrent calls for the same inspector (e.g. a mobile client retrying after a network
	 * timeout) could both see "no open shift" and both insert, breaking the "at most one open
	 * shift" rule. See that method's kdoc for the full reasoning (same fix shape as
	 * [uz.safecity.transportobserver.map.service.InspectorLocationService.upsertMyLocation]).
	 */
	@Transactional
	fun startShift(principal: CustomUserDetails): WorkShiftDto {
		assertInspector(principal.role)

		val id = UUID.randomUUID()
		val startedAt = Instant.now()
		val insertedRows = workShiftRepository.startIfNoOpenShift(
			id = id,
			inspectorId = principal.accountId,
			startedAt = startedAt,
			now = startedAt
		)
		if (insertedRows == 0) {
			throw ConflictException("error.shift.already-open")
		}

		return WorkShiftDto(id = id, inspectorId = principal.accountId, startedAt = startedAt, endedAt = null)
	}

	/**
	 * `POST /api/v1/inspector/me/shift/end`. Rejects with [ConflictException] if the caller has
	 * no open shift to close.
	 */
	@Transactional
	fun endShift(principal: CustomUserDetails): WorkShiftDto {
		assertInspector(principal.role)

		val shift = workShiftRepository.findByInspectorIdAndEndedAtIsNull(principal.accountId)
			?: throw ConflictException("error.shift.no-open-shift")

		shift.endedAt = Instant.now()
		return WorkShiftDto.from(workShiftRepository.save(shift))
	}

	/** `GET /api/v1/inspector/me/shift/current` — `null` (204, see controller) when there's no open shift. */
	fun getCurrent(principal: CustomUserDetails): WorkShiftDto? {
		assertInspector(principal.role)
		return workShiftRepository.findByInspectorIdAndEndedAtIsNull(principal.accountId)?.let { WorkShiftDto.from(it) }
	}

	/**
	 * Batch "who is currently on duty" lookup backing
	 * [uz.safecity.transportobserver.map.dto.EmployeeLocationDto.onDuty] (`GET /api/v1/map/employees`)
	 * and [uz.safecity.transportobserver.employees.dto.EmployeeDto.onDuty] (`GET /api/v1/employees`)
	 * — one grouped query per page/list instead of one per row. Safe to call with a mix of
	 * inspector and non-inspector account ids (non-inspectors simply never have a [WorkShift] row,
	 * so they never appear in the result).
	 */
	fun onDutyInspectorIds(accountIds: Collection<UUID>): Set<UUID> {
		if (accountIds.isEmpty()) return emptySet()
		return workShiftRepository.findByInspectorIdInAndEndedAtIsNull(accountIds)
			.map { it.inspectorId }
			.toSet()
	}

	/** Defense-in-depth mirror of the controller's `@PreAuthorize` — same pattern used throughout this codebase. */
	private fun assertInspector(role: RoleType) {
		if (role != RoleType.INSPECTOR) {
			throw ForbiddenException("error.shift.forbidden")
		}
	}
}

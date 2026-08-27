package uz.safecity.transportobserver.checkpoints.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.checkpoints.dto.CheckpointMetricsDto
import uz.safecity.transportobserver.checkpoints.dto.CheckpointOnDutyInspectorDto
import uz.safecity.transportobserver.checkpoints.dto.CheckpointTodayStatsDto
import uz.safecity.transportobserver.checkpoints.repository.CheckpointRepository
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.common.util.PresenceUtils
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.map.repository.InspectorLocationRepository
import uz.safecity.transportobserver.shifts.service.WorkShiftService
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Backs the mobile "Nazorat punkti" (checkpoint detail) screen (`GET /{id}/on-duty`,
 * `GET /{id}/today-stats`, `GET /{id}/metrics` on [uz.safecity.transportobserver.checkpoints.controller.CheckpointController]).
 * Kept as a SEPARATE service from [CheckpointService] — same split rationale as
 * [uz.safecity.transportobserver.reports.service.ReportStatsService] vs
 * [uz.safecity.transportobserver.reports.service.ReportService]: this class answers "live
 * per-checkpoint numbers right now" by reaching across the `shifts`/`inspections`/`auth`/`map`
 * modules, whereas [CheckpointService] is plain CRUD for the `checkpoints` table itself.
 *
 * IMPORTANT — honesty over completeness (see class-level TODOs on each DTO): this codebase has NO
 * Inspector-Checkpoint *permanent* assignment mechanism (see
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService] kdoc for that
 * long-standing gap) and [uz.safecity.transportobserver.incidents.entity.Incident] has NO
 * `checkpointId` column at all (unlike [uz.safecity.transportobserver.inspections.entity.Inspection],
 * which does). "On duty at this checkpoint" is answered ONLY via the new, optional, per-shift
 * [uz.safecity.transportobserver.shifts.entity.WorkShift.checkpointId] check-in (see that entity's
 * kdoc) — every other "checkpoint x inspector/incident" relationship this class is asked to
 * report on that has no honest data source returns `null`, not a fabricated `0` or a heuristic
 * guess. Do not "fix" a `null` field here by inventing a proximity/nearest-checkpoint heuristic —
 * that would produce plausible-looking but arbitrary numbers.
 */
@Service
class CheckpointStatsService(
	private val checkpointRepository: CheckpointRepository,
	private val workShiftService: WorkShiftService,
	private val accountRepository: AccountRepository,
	private val employeeRepository: EmployeeRepository,
	private val inspectorLocationRepository: InspectorLocationRepository,
	private val inspectionRepository: InspectionRepository
) {

	/**
	 * `GET /api/v1/checkpoints/{id}/on-duty` — every inspector with a currently-open
	 * [uz.safecity.transportobserver.shifts.entity.WorkShift] checked into [checkpointId]. See
	 * [CheckpointOnDutyInspectorDto] kdoc for how [CheckpointOnDutyInspectorDto.online] is computed
	 * (a DIFFERENT, independent signal from "on duty" — this whole list is already on-duty by
	 * definition, `online` answers "are they actually reachable right now" on top of that).
	 *
	 * Batched the same way as [uz.safecity.transportobserver.map.service.InspectorLocationService.listEmployeeLocations]:
	 * one query per signal for the whole roster, not one per inspector.
	 */
	fun getOnDuty(checkpointId: UUID): List<CheckpointOnDutyInspectorDto> {
		assertCheckpointExists(checkpointId)

		val openShifts = workShiftService.openShiftsForCheckpoint(checkpointId)
		if (openShifts.isEmpty()) return emptyList()

		val accountsById = accountRepository.findAllById(openShifts.map { it.inspectorId })
			.associateBy { requireNotNull(it.id) }
		val employeeIds = accountsById.values.mapNotNull { it.employeeId }
		val employeesById = employeeRepository.findAllById(employeeIds).associateBy { requireNotNull(it.id) }
		val locationsByInspectorId = inspectorLocationRepository.findByInspectorIdIn(accountsById.keys)
			.associateBy { it.inspectorId }
		val now = Instant.now()

		return openShifts.map { shift ->
			val account: Account? = accountsById[shift.inspectorId]
			val employee = account?.employeeId?.let { employeesById[it] }
			val location = locationsByInspectorId[shift.inspectorId]
			val online = PresenceUtils.isRecent(account?.lastActiveAt, now) ||
				PresenceUtils.isRecent(location?.updatedAt, now)
			val lastSeenAt = PresenceUtils.latestOf(account?.lastActiveAt, location?.updatedAt)

			CheckpointOnDutyInspectorDto(
				inspectorId = shift.inspectorId,
				fullName = employee?.fullName ?: "Noma'lum inspektor",
				position = employee?.position,
				shiftStartedAt = shift.startedAt,
				online = online,
				lastSeenAt = lastSeenAt
			)
		}
	}

	/**
	 * `GET /api/v1/checkpoints/{id}/today-stats` — "BUGUNGI HOLAT" block. [detectedIncidentsCount]/
	 * [averageInspectionDurationMinutes] are always `null` — see [CheckpointTodayStatsDto] kdoc for
	 * exactly why neither can be computed honestly today.
	 */
	fun getTodayStats(checkpointId: UUID): CheckpointTodayStatsDto {
		assertCheckpointExists(checkpointId)

		val (startOfToday, endOfToday) = dayRange(Instant.now())
		val completedTodayCount = inspectionRepository.countByCheckpointIdAndStatusAndPerformedAtBetween(
			checkpointId,
			InspectionStatus.COMPLETED,
			startOfToday,
			endOfToday
		)

		return CheckpointTodayStatsDto(
			checkpointId = checkpointId,
			onDutyInspectorsCount = workShiftService.openShiftsForCheckpoint(checkpointId).size,
			inspectionsCompletedTodayCount = completedTodayCount.toInt(),
			// No Incident.checkpointId / no Inspection "check duration" field — see kdoc above.
			detectedIncidentsCount = null,
			averageInspectionDurationMinutes = null,
			computedAt = Instant.now()
		)
	}

	/**
	 * `GET /api/v1/checkpoints/{id}/metrics` — "Tekshiruv/oy" / "Aniqlangan holat" / "Inspektor"
	 * header row. [detectedCasesCount] is always `null` — see [CheckpointMetricsDto] kdoc.
	 */
	fun getMetrics(checkpointId: UUID): CheckpointMetricsDto {
		assertCheckpointExists(checkpointId)

		val (startOfMonth, startOfNextMonth) = monthRange(Instant.now())
		val inspectionsThisMonth = inspectionRepository.countByCheckpointIdAndStatusAndPerformedAtBetween(
			checkpointId,
			InspectionStatus.COMPLETED,
			startOfMonth,
			startOfNextMonth
		)
		val distinctInspectors = inspectionRepository.countDistinctInspectorsByCheckpointIdAndCreatedAtBetween(
			checkpointId,
			startOfMonth,
			startOfNextMonth
		)

		return CheckpointMetricsDto(
			checkpointId = checkpointId,
			inspectionsThisMonthCount = inspectionsThisMonth.toInt(),
			// No Incident.checkpointId — see kdoc above.
			detectedCasesCount = null,
			inspectorsThisMonthCount = distinctInspectors.toInt()
		)
	}

	private fun assertCheckpointExists(checkpointId: UUID) {
		if (!checkpointRepository.existsById(checkpointId)) {
			throw ResourceNotFoundException("error.checkpoint.not-found", checkpointId)
		}
	}

	/** [Pair.first] = start of today (inclusive), [Pair.second] = start of tomorrow (exclusive), in [APP_ZONE]. */
	private fun dayRange(now: Instant): Pair<Instant, Instant> {
		val today = now.atZone(APP_ZONE).toLocalDate()
		val start = today.atStartOfDay(APP_ZONE).toInstant()
		val end = today.plusDays(1).atStartOfDay(APP_ZONE).toInstant()
		return start to end
	}

	/** [Pair.first] = start of the current calendar month (inclusive), [Pair.second] = start of next month (exclusive), in [APP_ZONE]. */
	private fun monthRange(now: Instant): Pair<Instant, Instant> {
		val firstOfMonth = now.atZone(APP_ZONE).toLocalDate().withDayOfMonth(1)
		val start = firstOfMonth.atStartOfDay(APP_ZONE).toInstant()
		val end = firstOfMonth.plusMonths(1).atStartOfDay(APP_ZONE).toInstant()
		return start to end
	}

	companion object {
		/**
		 * Same reasoning as [uz.safecity.transportobserver.inspector.service.InspectorPanelService.APP_ZONE] /
		 * [uz.safecity.transportobserver.reports.service.ReportStatsService.APP_ZONE]: the container's
		 * JVM default zone is UTC, not the inspectors' local time, so "today"/"this month" boundaries
		 * must be computed against a fixed zone, never `ZoneId.systemDefault()`.
		 */
		private val APP_ZONE: ZoneId = ZoneId.of("Asia/Tashkent")
	}
}

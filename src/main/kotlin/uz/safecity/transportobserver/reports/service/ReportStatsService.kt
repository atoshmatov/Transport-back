package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.checkpoints.repository.CheckpointRepository
import uz.safecity.transportobserver.common.dto.RegionCountProjection
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.employees.entity.EmployeeStatus
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.reports.dto.ActivityReportItemDto
import uz.safecity.transportobserver.reports.dto.CheckpointTypeDistributionItemDto
import uz.safecity.transportobserver.reports.dto.DashboardReportDto
import uz.safecity.transportobserver.reports.dto.RegionDistributionItemDto
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Backs the admin dashboard KPI cards + reports screens (TZ section 7) — currently hardcoded
 * sample data in `src/features/admin/dashboard/DashboardView.vue` /
 * `src/features/admin/reports/ReportsView.vue`. SUPER_ADMIN/ADMIN/OPERATOR only, see
 * [uz.safecity.transportobserver.reports.controller.ReportStatsController].
 *
 * Deliberately a SEPARATE class from [uz.safecity.transportobserver.reports.service.ReportService] /
 * [uz.safecity.transportobserver.reports.entity.Report] (which pre-date this task and back the
 * generated-report-FILE skeleton — async generation job, MinIO upload, `GET /reports`/`GET
 * /reports/{id}` listing those files): this service answers "live counts right now" queries
 * against the domain tables, not "list of previously-generated report documents". Both are
 * mounted under `/api/v1/reports` (different sub-paths) since they're the same TZ section 7 API
 * group, just two different controllers/services within it.
 *
 * Every "today"/"last N days" boundary in this file uses [APP_ZONE] (`Asia/Tashkent`), never
 * `ZoneId.systemDefault()` — same reasoning as
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService]'s `APP_ZONE`: the
 * container's JVM default zone is UTC (see docker-compose.yml), so using the host default would
 * silently shift day boundaries and mis-bucket rows created/completed near local midnight.
 *
 * `GET /reports/export` is intentionally not implemented here — TODO (next phase): file
 * generation/export is a separate, larger unit of work (likely extending the pre-existing
 * `Report`/`ReportService` skeleton mentioned above, not this class).
 */
@Service
class ReportStatsService(
	private val employeeRepository: EmployeeRepository,
	private val accountRepository: AccountRepository,
	private val inspectionRepository: InspectionRepository,
	private val incidentRepository: IncidentRepository,
	private val checkpointRepository: CheckpointRepository
) {

	/**
	 * Backs the 5-card KPI row (mockup: "Jami xodimlar" / "Xaritadagi xodimlar" / "Tekshiruvlar
	 * (bugun)" / "Aniqlangan holatlar" / "Favqulodda hodisalar"). Every number below is a real
	 * query against domain tables — none of it is hardcoded/mocked.
	 *
	 * - `totalEmployeesCount`: [EmployeeRepository.countByStatusNot] excluding DISMISSED — see its
	 *   kdoc for why dismissed staff don't count toward "jami xodimlar".
	 * - `activeEmployeesCount`: [AccountRepository.countByIsActiveTrueAndEmployeeIdIsNotNull] — see
	 *   its kdoc for why this counts accounts rather than joining through Employee directly.
	 * - `onlineEmployeesCount`: PROXY, always equal to `activeEmployeesCount` — see
	 *   [DashboardReportDto.onlineEmployeesCount] kdoc. There is no real-time presence/location
	 *   tracking system yet (TZ section 7's future `POST /map/position`), so "onlayn xodimlar"
	 *   cannot be computed from real data today. Deliberately not faked with an arbitrary
	 *   number/percentage — this reuses the nearest real, honest figure instead.
	 * - `todayInspectionsCount`: COMPLETED [uz.safecity.transportobserver.inspections.entity.Inspection]s
	 *   whose `performedAt` falls within today's `[start, end)` window in [APP_ZONE].
	 * - `totalInspectionsCount`: [InspectionRepository.count] — every inspection, any status.
	 * - `detectedViolationsCount` / `emergencyIncidentsCount`: split the domain's
	 *   [IncidentType] values into two non-overlapping buckets so the two dashboard cards never
	 *   double-count the same incident:
	 *     - "Aniqlangan holatlar" (detected violations/faults) = VIOLATION + TECHNICAL_FAULT +
	 *       OTHER, ANY status, ALL-time — these are routine findings, not life/safety emergencies,
	 *       so the running total (like a violations log) is more useful here than "currently open".
	 *     - "Favqulodda hodisalar" (emergencies) = ACCIDENT + SECURITY, but only NEW/IN_PROGRESS
	 *       ("Faol holatlar" = still-active emergencies) — a RESOLVED/REJECTED accident is no
	 *       longer an active emergency, so unlike the violations card this one IS status-filtered.
	 *   (Alternative considered: split by status instead of type, i.e. all-time count vs.
	 *   currently-open count of the *same* types. Rejected because the mockup explicitly labels
	 *   card 5 "Favqulodda hodisalar" — a distinct, narrower concept from "aniqlangan holatlar" —
	 *   not just "the open subset of the same bucket"; splitting by type keeps the two cards
	 *   answering genuinely different questions.)
	 * - `totalIncidentsCount`: [IncidentRepository.count] — every incident, any type, any status.
	 * - `blockedAccountsCount`: accounts with `isActive = false`.
	 */
	fun getDashboard(): DashboardReportDto {
		val (startOfToday, endOfToday) = dayRange(LocalDate.now(APP_ZONE))
		val activeEmployeesCount = accountRepository.countByIsActiveTrueAndEmployeeIdIsNotNull().toInt()
		return DashboardReportDto(
			totalEmployeesCount = employeeRepository.countByStatusNot(EmployeeStatus.DISMISSED).toInt(),
			activeEmployeesCount = activeEmployeesCount,
			onlineEmployeesCount = activeEmployeesCount, // proxy — see kdoc above
			todayInspectionsCount = inspectionRepository.countByStatusAndPerformedAtBetween(
				InspectionStatus.COMPLETED, startOfToday, endOfToday
			).toInt(),
			totalInspectionsCount = inspectionRepository.count().toInt(),
			detectedViolationsCount = incidentRepository.countByTypeIn(DETECTED_VIOLATION_TYPES).toInt(),
			totalIncidentsCount = incidentRepository.count().toInt(),
			emergencyIncidentsCount = incidentRepository.countByTypeInAndStatusIn(
				EMERGENCY_INCIDENT_TYPES, listOf(IncidentStatus.NEW, IncidentStatus.IN_PROGRESS)
			).toInt(),
			blockedAccountsCount = accountRepository.countByIsActive(false).toInt(),
			lastUpdatedAt = Instant.now()
		)
	}

	/**
	 * [range] currently only accepts `"7d"` — TZ section 7 leaves the parameter open for future
	 * ranges (e.g. `30d`), but no screen needs them yet, so anything else is rejected explicitly
	 * rather than silently falling back to 7 days (which would hide a frontend bug).
	 *
	 * Fetches the 7-day window's inspections/incidents with a single `GROUP BY` query each (see
	 * [InspectionRepository.countDailyCreatedBetween] / [IncidentRepository.countDailyCreatedBetween]),
	 * then fills in every day of the window — including days with zero activity, which the `GROUP BY`
	 * queries simply omit — so the frontend never has to handle missing days in the series.
	 */
	fun getActivity(range: String): List<ActivityReportItemDto> {
		if (range != "7d") {
			throw BadRequestException("error.report.range-unsupported")
		}

		val today = LocalDate.now(APP_ZONE)
		val firstDay = today.minusDays(6) // 7-day window, inclusive of today
		val (start, _) = dayRange(firstDay)
		val (_, end) = dayRange(today)

		val inspectionsByDay = inspectionRepository.countDailyCreatedBetween(start, end)
			.associate { it.day to it.cnt }
		val incidentsByDay = incidentRepository.countDailyCreatedBetween(start, end)
			.associate { it.day to it.cnt }

		return (0..6L).map { offset ->
			val day = firstDay.plusDays(offset)
			ActivityReportItemDto(
				date = day,
				inspectionsCount = (inspectionsByDay[day] ?: 0L).toInt(),
				incidentsCount = (incidentsByDay[day] ?: 0L).toInt()
			)
		}
	}

	/**
	 * Merges three independent `GROUP BY regionName` queries (Employee/Checkpoint/Incident) — there
	 * is no real `regions` table yet to join through (all three carry a plain-text `regionName`
	 * column, see each entity's TODO kdoc), so this is three cheap aggregate queries, not a fan-out
	 * per region. A region missing from one entity's grouping simply contributes 0 to that entity's
	 * count in the merged row (e.g. a region with checkpoints but no incidents yet).
	 *
	 * A null `regionName` (row never assigned a region) is relabeled to [UNKNOWN_REGION_LABEL] per
	 * entity, and — since a `GROUP BY` naturally collapses all nulls into one row already — that
	 * single relabeled bucket per entity is what gets merged into the combined "Belgilanmagan" row,
	 * i.e. every unregioned employee/checkpoint/incident lands in ONE combined row, not three
	 * separate ones.
	 */
	fun getRegionsDistribution(): List<RegionDistributionItemDto> {
		val employeeCounts = byLabel(employeeRepository.countGroupByRegion())
		val checkpointCounts = byLabel(checkpointRepository.countGroupByRegion())
		val incidentCounts = byLabel(incidentRepository.countGroupByRegion())

		val allRegions = employeeCounts.keys + checkpointCounts.keys + incidentCounts.keys

		return allRegions
			.map { region ->
				RegionDistributionItemDto(
					regionName = region,
					employeesCount = (employeeCounts[region] ?: 0L).toInt(),
					checkpointsCount = (checkpointCounts[region] ?: 0L).toInt(),
					incidentsCount = (incidentCounts[region] ?: 0L).toInt()
				)
			}
			// Alphabetical, with the "Belgilanmagan" catch-all pushed to the end regardless of
			// where it'd otherwise sort — it's not a real region name, so it reads oddly interleaved
			// with actual ones.
			.sortedWith(compareBy({ it.regionName == UNKNOWN_REGION_LABEL }, { it.regionName }))
	}

	private fun byLabel(rows: List<RegionCountProjection>): Map<String, Long> =
		rows.associate { (it.regionName ?: UNKNOWN_REGION_LABEL) to it.cnt }

	/**
	 * Map/dashboard checkpoint-category legend (mockup: "Avtovokzallardagi xodimlar",
	 * "Temiryo'l vokzallaridagi xodimlar", "Magistral yo'llardagi xodimlar", "Aeroportlardagi
	 * xodimlar"). Groups active [uz.safecity.transportobserver.checkpoints.entity.Checkpoint]s by
	 * their free-form `type` column (see [CheckpointRepository.countActiveGroupByType] kdoc).
	 *
	 * Deliberately counts CHECKPOINTS per category, not "employees at that category's checkpoints"
	 * (which is literally what the mockup's numbers represent) — this system has no per-checkpoint
	 * employee headcount or real-time assignment/location tracking to compute that from yet (same
	 * gap as `onlineEmployeesCount`, see [getDashboard] kdoc). Checkpoint count is the closest
	 * real, honest number available today; wiring this to actual per-category staffing is a TODO
	 * once presence-tracking (TZ section 7, future `POST /map/position`) exists.
	 *
	 * TODO (data quality, not code): `Checkpoint.type` is free text with no enforced vocabulary —
	 * as of now nothing guarantees rows use consistent values like "BUS_STATION"/"TRAIN_STATION"/
	 * "HIGHWAY"/"AIRPORT". This method just groups whatever strings are actually stored; if no
	 * checkpoint has been tagged with a real category yet, every active checkpoint falls into the
	 * null/[UNKNOWN_REGION_LABEL]-style "Belgilanmagan" bucket below. Standardizing `type` values
	 * (admin UI dropdown, or promoting the column to a real enum) is left for a later pass — not
	 * required to serve real, ungrouped data today.
	 */
	fun getCheckpointsDistribution(): List<CheckpointTypeDistributionItemDto> =
		checkpointRepository.countActiveGroupByType()
			.map { CheckpointTypeDistributionItemDto(type = it.type ?: UNKNOWN_REGION_LABEL, count = it.cnt.toInt()) }
			.sortedWith(compareBy({ it.type == UNKNOWN_REGION_LABEL }, { it.type }))

	/** [Pair.first] = start of [day] (inclusive), [Pair.second] = start of the next day (exclusive), in [APP_ZONE]. */
	private fun dayRange(day: LocalDate): Pair<Instant, Instant> {
		val start = day.atStartOfDay(APP_ZONE).toInstant()
		val end = day.plusDays(1).atStartOfDay(APP_ZONE).toInstant()
		return start to end
	}

	companion object {
		private val APP_ZONE: ZoneId = ZoneId.of("Asia/Tashkent")
		const val UNKNOWN_REGION_LABEL = "Belgilanmagan"

		/** "Aniqlangan holatlar" dashboard card — see [getDashboard] kdoc for the type-split rationale. */
		private val DETECTED_VIOLATION_TYPES =
			listOf(IncidentType.VIOLATION, IncidentType.TECHNICAL_FAULT, IncidentType.OTHER)

		/** "Favqulodda hodisalar" dashboard card — see [getDashboard] kdoc for the type-split rationale. */
		private val EMERGENCY_INCIDENT_TYPES = listOf(IncidentType.ACCIDENT, IncidentType.SECURITY)
	}
}

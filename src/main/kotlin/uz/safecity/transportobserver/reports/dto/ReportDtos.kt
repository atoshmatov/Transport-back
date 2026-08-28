package uz.safecity.transportobserver.reports.dto

import java.time.Instant
import java.time.LocalDate

/**
 * `GET /api/v1/reports/dashboard` — the 5 admin-dashboard KPI cards (mockup: "Jami xodimlar",
 * "Xaritadagi xodimlar", "Tekshiruvlar (bugun)", "Aniqlangan holatlar", "Favqulodda hodisalar").
 * See [uz.safecity.transportobserver.reports.service.ReportStatsService.getDashboard] kdoc for
 * each field's exact definition/query — every field here is computed from real data, no mocked
 * values.
 */
data class DashboardReportDto(
	/** Card 1 main number ("Jami xodimlar") — current staff, [EmployeeStatus.DISMISSED] excluded. */
	val totalEmployeesCount: Int,
	/** Card 1 subtext ("Faol: X") — accounts with `isActive = true`. */
	val activeEmployeesCount: Int,
	/**
	 * Card 2 ("Xaritadagi xodimlar" / "Onlayn"). PROXY VALUE — currently always equal to
	 * [activeEmployeesCount]. There is no real-time presence/location-heartbeat system yet (TZ
	 * section 7's future `POST /map/position` + `/topic/positions`), so "onlayn" cannot be
	 * computed from live data today; this deliberately reuses the closest real number instead of
	 * fabricating one. Remove this proxy and compute it for real once presence-tracking exists.
	 */
	val onlineEmployeesCount: Int,
	/** Card 3 main number ("Tekshiruvlar (bugun)") — COMPLETED inspections performed today. */
	val todayInspectionsCount: Int,
	/** Card 3 subtext ("Jami: X") — all inspections, any status. */
	val totalInspectionsCount: Int,
	/**
	 * Card 4 main number ("Aniqlangan holatlar") — all-time count of incidents whose type is
	 * VIOLATION, TECHNICAL_FAULT, or OTHER (any status). See [ReportStatsService.getDashboard]
	 * kdoc for why the incident type set is split this way between this field and
	 * [emergencyIncidentsCount].
	 */
	val detectedViolationsCount: Int,
	/** Card 4 subtext ("Jami: X") — all incidents, any type, any status. */
	val totalIncidentsCount: Int,
	/**
	 * Card 5 ("Favqulodda hodisalar" / "Faol holatlar") — incidents whose type is ACCIDENT or
	 * SECURITY AND whose status is still NEW or IN_PROGRESS (i.e. currently active/unresolved).
	 */
	val emergencyIncidentsCount: Int,
	/** Not on the 5-card row (kept for potential future use) — accounts with `isActive = false`. */
	val blockedAccountsCount: Int,
	val lastUpdatedAt: Instant
)

/**
 * One data point of `GET /api/v1/reports/activity?range=`. For `range=7d`/`30d`, [date] is a
 * calendar day (`Asia/Tashkent`) and there is one point per day in the window. For `range=1y`,
 * [date] is the first day of a calendar month and there is one point per month over the trailing
 * 12 months — see [uz.safecity.transportobserver.reports.service.ReportStatsService.getActivity]
 * kdoc.
 */
data class ActivityReportItemDto(
	val date: LocalDate,
	val inspectionsCount: Int,
	val incidentsCount: Int
)

/**
 * One region's row of `GET /api/v1/reports/regions-distribution`. [regionName] is
 * "Belgilanmagan" for the merged bucket of rows whose underlying `regionName` column is null —
 * see [uz.safecity.transportobserver.reports.service.ReportService.getRegionsDistribution] kdoc.
 */
data class RegionDistributionItemDto(
	val regionName: String,
	val employeesCount: Int,
	val checkpointsCount: Int,
	val incidentsCount: Int
)

/**
 * One category's row of `GET /api/v1/reports/checkpoints-distribution` — mockup's map legend
 * ("Avtovokzallardagi xodimlar", "Temiryo'l vokzallaridagi xodimlar", "Magistral yo'llardagi
 * xodimlar", "Aeroportlardagi xodimlar"). [type] is [uz.safecity.transportobserver.checkpoints.entity.Checkpoint.type]
 * as-is (free-form string, "Belgilanmagan" for null) — see
 * [uz.safecity.transportobserver.reports.service.ReportStatsService.getCheckpointsDistribution]
 * kdoc for why this counts checkpoints (a real, existing field) rather than per-category
 * employee headcounts (which the mockup shows but which would require real-time
 * location/assignment tracking this system doesn't have yet).
 */
data class CheckpointTypeDistributionItemDto(
	val type: String,
	val count: Int
)

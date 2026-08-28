package uz.safecity.transportobserver.reports.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.reports.dto.ActivityReportItemDto
import uz.safecity.transportobserver.reports.dto.CheckpointTypeDistributionItemDto
import uz.safecity.transportobserver.reports.dto.DashboardReportDto
import uz.safecity.transportobserver.reports.dto.RegionDistributionItemDto
import uz.safecity.transportobserver.reports.service.ReportStatsService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Admin dashboard + reports screens (TZ section 7): `GET /dashboard`, `GET /activity`,
 * `GET /regions-distribution`, `GET /checkpoints-distribution`. `GET /dashboard` stays
 * SUPER_ADMIN/ADMIN/OPERATOR-only — these are dispatch/management KPI cards, not something a
 * field INSPECTOR needs, same role set as
 * [uz.safecity.transportobserver.inspections.controller.InspectionController]'s `create` endpoint.
 *
 * `GET /activity`, `GET /regions-distribution` and `GET /checkpoints-distribution` additionally
 * allow ROLE_INSPECTOR — the mobile TransportO app's Stats screen (INSPECTOR-only accounts) calls
 * these three. Safe to open up: each one is a pure aggregate/count query with no per-caller or
 * per-region filtering to begin with (see [ReportStatsService.getActivity]/
 * [ReportStatsService.getRegionsDistribution]/[ReportStatsService.getCheckpointsDistribution]
 * kdocs) — an ADMIN/OPERATOR already sees the exact same all-regions/all-time totals an INSPECTOR
 * would, so there is no individual-inspector or incident-level detail being newly exposed here.
 * Same "add ROLE_INSPECTOR to an existing aggregate-stats endpoint" pattern previously applied to
 * `CheckpointStatsApi`'s `on-duty`/`today-stats`/`metrics` endpoints.
 *
 * Mounted on the same `/api/v1/reports` base path as the pre-existing
 * [uz.safecity.transportobserver.reports.controller.ReportController] (generated-report-FILE
 * listing — `GET /reports`, `GET /reports/{id}`) since both belong to the same TZ section 7 API
 * group; kept as a separate controller/service pair (see [ReportStatsService] kdoc) rather than
 * merged into that one, since "live KPI counts" and "list of previously generated report
 * documents" are different concerns with nothing in common beyond the URL prefix.
 *
 * `GET /reports/export` is deliberately not implemented yet — TODO (next phase): file
 * generation/export is a separate, larger unit of work.
 */
@RestController
@RequestMapping("/api/v1/reports")
class ReportStatsController(
	private val reportStatsService: ReportStatsService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/dashboard")
	fun dashboard(): ResponseEntity<ApiResponse<DashboardReportDto>> =
		ResponseEntity.ok(ApiResponse.ok(reportStatsService.getDashboard()))

	// range: "7d" (default) / "30d" -> one point per day; "1y" -> one point per calendar month.
	// See ReportStatsService#getActivity kdoc. ROLE_INSPECTOR allowed — see class kdoc.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/activity")
	fun activity(
		@RequestParam(defaultValue = "7d") range: String
	): ResponseEntity<ApiResponse<List<ActivityReportItemDto>>> =
		ResponseEntity.ok(ApiResponse.ok(reportStatsService.getActivity(range)))

	// ROLE_INSPECTOR allowed — see class kdoc.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/regions-distribution")
	fun regionsDistribution(): ResponseEntity<ApiResponse<List<RegionDistributionItemDto>>> =
		ResponseEntity.ok(ApiResponse.ok(reportStatsService.getRegionsDistribution()))

	/**
	 * Map/dashboard checkpoint-category legend — see [ReportStatsService.getCheckpointsDistribution]
	 * kdoc. Also allows ROLE_INSPECTOR (mobile Stats screen) — see class kdoc — even though the raw
	 * checkpoint list it's derived from
	 * ([uz.safecity.transportobserver.map.controller.MapController]'s `GET /map/checkpoints`) is a
	 * separate, differently-shaped endpoint for a different use case ("where are the checkpoints on
	 * my map" vs. this one's admin-dashboard-style aggregate legend).
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/checkpoints-distribution")
	fun checkpointsDistribution(): ResponseEntity<ApiResponse<List<CheckpointTypeDistributionItemDto>>> =
		ResponseEntity.ok(ApiResponse.ok(reportStatsService.getCheckpointsDistribution()))
}

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
 * `GET /regions-distribution`, `GET /checkpoints-distribution`. SUPER_ADMIN/ADMIN/OPERATOR only —
 * these are dispatch/management views, not something a field INSPECTOR needs, same role set as
 * [uz.safecity.transportobserver.inspections.controller.InspectionController]'s `create` endpoint.
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
	// See ReportStatsService#getActivity kdoc.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/activity")
	fun activity(
		@RequestParam(defaultValue = "7d") range: String
	): ResponseEntity<ApiResponse<List<ActivityReportItemDto>>> =
		ResponseEntity.ok(ApiResponse.ok(reportStatsService.getActivity(range)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/regions-distribution")
	fun regionsDistribution(): ResponseEntity<ApiResponse<List<RegionDistributionItemDto>>> =
		ResponseEntity.ok(ApiResponse.ok(reportStatsService.getRegionsDistribution()))

	/**
	 * Map/dashboard checkpoint-category legend — see [ReportStatsService.getCheckpointsDistribution]
	 * kdoc. Kept SUPER_ADMIN/ADMIN/OPERATOR-only for consistency with the rest of this controller's
	 * dispatch/management views, even though the raw checkpoint list it's derived from
	 * ([uz.safecity.transportobserver.map.controller.MapController]'s `GET /map/checkpoints`) is
	 * open to INSPECTOR too — this endpoint answers an admin-dashboard aggregate question, not
	 * "where are the checkpoints on my map", which is the INSPECTOR-facing use case.
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/checkpoints-distribution")
	fun checkpointsDistribution(): ResponseEntity<ApiResponse<List<CheckpointTypeDistributionItemDto>>> =
		ResponseEntity.ok(ApiResponse.ok(reportStatsService.getCheckpointsDistribution()))
}

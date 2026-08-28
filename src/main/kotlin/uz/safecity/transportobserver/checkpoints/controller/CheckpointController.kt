package uz.safecity.transportobserver.checkpoints.controller

import uz.safecity.transportobserver.checkpoints.dto.CheckpointDto
import uz.safecity.transportobserver.checkpoints.dto.CheckpointMetricsDto
import uz.safecity.transportobserver.checkpoints.dto.CheckpointNearbyDto
import uz.safecity.transportobserver.checkpoints.dto.CheckpointOnDutyInspectorDto
import uz.safecity.transportobserver.checkpoints.dto.CheckpointTodayStatsDto
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.common.dto.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Admin-section checkpoint roster, read-only (TZ section 6, "Nazorat/transport"),
 * open to SUPER_ADMIN/ADMIN/OPERATOR. Admin-exclusive write endpoints
 * (create/update/status) live on
 * [uz.safecity.transportobserver.checkpoints.controller.AdminCheckpointController]
 * under `/api/v1/admin/checkpoints` — see that controller's kdoc for the split rationale.
 *
 * The public map view is served separately by
 * [uz.safecity.transportobserver.map.controller.MapController] (`GET /api/v1/map/checkpoints`),
 * which is open to every authenticated role, including INSPECTOR — see that
 * controller's kdoc for why this module is split across multiple controllers.
 */
@RestController
@RequestMapping("/api/v1/checkpoints")
class CheckpointController(
	private val checkpointService: CheckpointService,
	private val checkpointStatsService: CheckpointStatsService
) {

	// TZ 5.6-bo'lim (ruxsatlar matritsasi): the full checkpoint roster is an
	// Admin/Operator capability — INSPECTOR must not list every checkpoint via
	// this endpoint (same pattern as EmployeeController.list).
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping
	fun list(
		@RequestParam(required = false) regionName: String?,
		@RequestParam(required = false) type: String?,
		@RequestParam(required = false) isActive: Boolean?,
		@RequestParam(required = false) checkpointTypeId: UUID?,
		@PageableDefault(size = 20) pageable: Pageable
	): ResponseEntity<ApiResponse<PageResponse<CheckpointDto>>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.list(regionName, type, isActive, checkpointTypeId, pageable)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<CheckpointDto>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.getById(id)))

	/**
	 * Mobile "hodisa yaratish" (report incident) flow's nearest-checkpoint SUGGESTION list — see
	 * [uz.safecity.transportobserver.incidents.entity.Incident.checkpointId] kdoc for the full
	 * hybrid-approach reasoning: this is the GPS-proximity ranking the client pre-fills/pre-selects
	 * with, but the field inspector must confirm or change it before `POST /incidents` is called —
	 * this endpoint itself never saves anything. Declared as a literal `/nearby` segment (matched
	 * ahead of the `/{id}` UUID path-variable route by Spring's path-pattern specificity, same
	 * pattern as any `/me`-style literal-vs-variable split elsewhere in this codebase).
	 *
	 * Open to `ROLE_INSPECTOR` too — same reasoning as [getOnDuty]/[getTodayStats]/[getMetrics]:
	 * this backs a screen every field inspector reaches directly, not an admin-only roster.
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/nearby")
	fun nearby(
		@RequestParam latitude: Double,
		@RequestParam longitude: Double,
		@RequestParam(required = false, defaultValue = "5") limit: Int
	): ResponseEntity<ApiResponse<List<CheckpointNearbyDto>>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.findNearby(latitude, longitude, limit)))

	/**
	 * Mobile "Nazorat punkti" screen's "Navbatchi inspektorlar" list — see
	 * [CheckpointStatsService.getOnDuty] kdoc.
	 *
	 * Unlike [list]/[getById] (the admin-only full roster — TZ 5.6), this one is deliberately
	 * ALSO open to `ROLE_INSPECTOR`: it backs the mobile pointDetail screen that every field
	 * inspector reaches from the map, not an admin console. It leaks nothing the admin-only
	 * endpoints don't already: [CheckpointOnDutyInspectorDto]/[CheckpointTodayStatsDto]/
	 * [CheckpointMetricsDto] carry only this one checkpoint's own roster/aggregate numbers, the
	 * same shape as e.g. [uz.safecity.transportobserver.map.controller.MapController]'s
	 * inspector-visible endpoints.
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}/on-duty")
	fun getOnDuty(@PathVariable id: UUID): ResponseEntity<ApiResponse<List<CheckpointOnDutyInspectorDto>>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointStatsService.getOnDuty(id)))

	/**
	 * Mobile "Nazorat punkti" screen's "BUGUNGI HOLAT" block — see
	 * [CheckpointStatsService.getTodayStats] kdoc (incl. why one of its fields is always `null`).
	 * Also open to `ROLE_INSPECTOR` — see [getOnDuty] kdoc for why.
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}/today-stats")
	fun getTodayStats(@PathVariable id: UUID): ResponseEntity<ApiResponse<CheckpointTodayStatsDto>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointStatsService.getTodayStats(id)))

	/**
	 * Mobile "Nazorat punkti" screen's header metrics ("Tekshiruv/oy" / "Aniqlangan holat" /
	 * "Inspektor") — see [CheckpointStatsService.getMetrics] kdoc.
	 * Also open to `ROLE_INSPECTOR` — see [getOnDuty] kdoc for why.
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}/metrics")
	fun getMetrics(@PathVariable id: UUID): ResponseEntity<ApiResponse<CheckpointMetricsDto>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointStatsService.getMetrics(id)))
}

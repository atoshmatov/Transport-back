package uz.safecity.transportobserver.inspector.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.inspector.dto.ActionTypeCountDto
import uz.safecity.transportobserver.inspector.dto.DashboardSummaryDto
import uz.safecity.transportobserver.inspector.dto.IncidentTypeCountDto
import uz.safecity.transportobserver.inspector.dto.InspectorCurrentLocationDto
import uz.safecity.transportobserver.inspector.dto.InspectorStatsDto
import uz.safecity.transportobserver.inspector.dto.RecentActivityDto
import uz.safecity.transportobserver.inspector.service.InspectorPanelService
import uz.safecity.transportobserver.map.dto.UpdateInspectorLocationRequest
import uz.safecity.transportobserver.map.service.InspectorLocationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Inspector web-panel dashboard + map screens. Every endpoint here is
 * INSPECTOR-only and scoped to the caller's own assigned incidents — see
 * [InspectorPanelService] kdoc for the scoping pattern. `/me/location` is
 * scoped the same way but backed by [InspectorLocationService] (`map` module)
 * instead — see that service's kdoc.
 */
@RestController
@RequestMapping("/api/v1/inspector")
class InspectorPanelController(
	private val inspectorPanelService: InspectorPanelService,
	private val inspectorLocationService: InspectorLocationService
) {

	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/dashboard/summary")
	fun getDashboardSummary(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<DashboardSummaryDto>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getDashboardSummary(principal)))

	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/map/current-location")
	fun getCurrentLocation(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<InspectorCurrentLocationDto>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getCurrentLocation(principal)))

	/** Mobile Profile screen "5 asosiy metrik" block — see [InspectorStatsDto] kdoc. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/stats")
	fun getMyStats(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<InspectorStatsDto>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getMyStats(principal)))

	/** Mobile Profile screen "hodisa turi bo'yicha" donut chart — see [IncidentTypeCountDto] kdoc. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/incident-type-breakdown")
	fun getMyIncidentTypeBreakdown(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<List<IncidentTypeCountDto>>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getIncidentTypeBreakdown(principal)))

	/** Mobile Profile screen "harakat turi bo'yicha" donut chart — see [ActionTypeCountDto] kdoc. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/action-type-breakdown")
	fun getMyActionTypeBreakdown(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<List<ActionTypeCountDto>>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getActionTypeBreakdown(principal)))

	/** Mobile Profile screen "so'nggi ishlar" list — see [RecentActivityDto] kdoc. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/recent-activity")
	fun getMyRecentActivity(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<List<RecentActivityDto>>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getRecentActivity(principal)))

	/**
	 * Periodic foreground-location heartbeat from the mobile app (per task scope: every 1-2 min).
	 * Upserts the caller's own single latest-position row — see [InspectorLocationService] kdoc.
	 * `204 No Content` on success, same "no meaningful body" convention as
	 * [uz.safecity.transportobserver.auth.controller.AuthController]'s ack endpoints.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@PostMapping("/me/location")
	fun updateMyLocation(
		@AuthenticationPrincipal principal: CustomUserDetails,
		@Valid @RequestBody request: UpdateInspectorLocationRequest
	): ResponseEntity<Void> {
		inspectorLocationService.upsertMyLocation(principal, request)
		return ResponseEntity.noContent().build()
	}
}

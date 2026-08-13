package uz.safecity.transportobserver.inspector.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.inspector.dto.DashboardSummaryDto
import uz.safecity.transportobserver.inspector.dto.InspectorCurrentLocationDto
import uz.safecity.transportobserver.inspector.service.InspectorPanelService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Inspector web-panel dashboard + map screens. Every endpoint here is
 * INSPECTOR-only and scoped to the caller's own assigned incidents — see
 * [InspectorPanelService] kdoc for the scoping pattern.
 */
@RestController
@RequestMapping("/api/v1/inspector")
class InspectorPanelController(
	private val inspectorPanelService: InspectorPanelService
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
}

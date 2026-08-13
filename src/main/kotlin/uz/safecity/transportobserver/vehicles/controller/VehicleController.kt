package uz.safecity.transportobserver.vehicles.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.vehicles.dto.VehicleDto
import uz.safecity.transportobserver.vehicles.entity.VehicleType
import uz.safecity.transportobserver.vehicles.service.VehicleService
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
 * Admin-section vehicle registry, read-only (TZ section 6, "Nazorat/transport"),
 * open to SUPER_ADMIN/ADMIN/OPERATOR. Admin-exclusive write endpoints
 * (create/update/status) live on
 * [uz.safecity.transportobserver.vehicles.controller.AdminVehicleController]
 * under `/api/v1/admin/vehicles` — see that controller's kdoc for the split rationale.
 * Same split-controller pattern as
 * [uz.safecity.transportobserver.checkpoints.controller.CheckpointController], while a
 * broader, unauthenticated-role-agnostic view (if/when needed) belongs on
 * [uz.safecity.transportobserver.map.controller.MapController].
 */
@RestController
@RequestMapping("/api/v1/vehicles")
class VehicleController(
	private val vehicleService: VehicleService
) {

	// TZ 5.6-bo'lim (ruxsatlar matritsasi): the full vehicle roster is an
	// Admin/Operator capability, same as CheckpointController.list/EmployeeController.list —
	// INSPECTOR must not list every vehicle via this endpoint.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping
	fun list(
		@RequestParam(required = false) type: VehicleType?,
		@RequestParam(required = false) isActive: Boolean?,
		@RequestParam(required = false) regionName: String?,
		@RequestParam(required = false) assignedEmployeeId: UUID?,
		@PageableDefault(size = 20) pageable: Pageable
	): ResponseEntity<ApiResponse<PageResponse<VehicleDto>>> =
		ResponseEntity.ok(ApiResponse.ok(vehicleService.list(type, isActive, regionName, assignedEmployeeId, pageable)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<VehicleDto>> =
		ResponseEntity.ok(ApiResponse.ok(vehicleService.getById(id)))
}

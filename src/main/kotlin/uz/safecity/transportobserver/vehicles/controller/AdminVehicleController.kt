package uz.safecity.transportobserver.vehicles.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.vehicles.dto.CreateVehicleRequest
import uz.safecity.transportobserver.vehicles.dto.UpdateVehicleRequest
import uz.safecity.transportobserver.vehicles.dto.UpdateVehicleStatusRequest
import uz.safecity.transportobserver.vehicles.dto.VehicleDto
import uz.safecity.transportobserver.vehicles.service.VehicleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Admin-exclusive vehicle management (create/update/status) — split out of
 * [VehicleController] onto the `/api/v1/admin/vehicles` namespace so that admin-only
 * write endpoints are not mixed under the same base path as the SUPER_ADMIN/ADMIN/OPERATOR
 * read endpoints ([VehicleController.list]/[VehicleController.getById]).
 *
 * Every endpoint here is SUPER_ADMIN/ADMIN only (OPERATOR and INSPECTOR excluded).
 * Delegates to the same [VehicleService] — only the routing/URL prefix changed here,
 * no service-layer behavior.
 */
@RestController
@RequestMapping("/api/v1/admin/vehicles")
class AdminVehicleController(
	private val vehicleService: VehicleService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping
	fun create(@Valid @RequestBody request: CreateVehicleRequest): ResponseEntity<ApiResponse<VehicleDto>> =
		ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(vehicleService.create(request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PutMapping("/{id}")
	fun update(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateVehicleRequest
	): ResponseEntity<ApiResponse<VehicleDto>> =
		ResponseEntity.ok(ApiResponse.ok(vehicleService.update(id, request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PatchMapping("/{id}/status")
	fun updateStatus(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateVehicleStatusRequest
	): ResponseEntity<ApiResponse<VehicleDto>> =
		ResponseEntity.ok(ApiResponse.ok(vehicleService.updateStatus(id, requireNotNull(request.isActive))))
}

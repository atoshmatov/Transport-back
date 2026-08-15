package uz.safecity.transportobserver.regions.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.regions.dto.CreateRegionRequest
import uz.safecity.transportobserver.regions.dto.RegionDto
import uz.safecity.transportobserver.regions.dto.UpdateRegionRequest
import uz.safecity.transportobserver.regions.service.RegionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/regions")
class AdminRegionController(
	private val regionService: RegionService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping
	fun create(@Valid @RequestBody request: CreateRegionRequest): ResponseEntity<ApiResponse<RegionDto>> =
		ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(regionService.create(request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PutMapping("/{id}")
	fun update(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateRegionRequest
	): ResponseEntity<ApiResponse<RegionDto>> =
		ResponseEntity.ok(ApiResponse.ok(regionService.update(id, request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@DeleteMapping("/{id}")
	fun delete(@PathVariable id: UUID): ResponseEntity<ApiResponse<Boolean>> {
		regionService.delete(id)
		return ResponseEntity.ok(ApiResponse.ok(true))
	}
}

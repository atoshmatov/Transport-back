package uz.safecity.transportobserver.regions.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.regions.dto.RegionDto
import uz.safecity.transportobserver.regions.service.RegionService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/regions")
class RegionController(
	private val regionService: RegionService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping
	fun listAll(): ResponseEntity<ApiResponse<List<RegionDto>>> =
		ResponseEntity.ok(ApiResponse.ok(regionService.listAll()))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<RegionDto>> =
		ResponseEntity.ok(ApiResponse.ok(regionService.getById(id)))
}

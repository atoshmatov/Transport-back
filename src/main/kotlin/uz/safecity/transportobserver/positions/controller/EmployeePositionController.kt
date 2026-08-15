package uz.safecity.transportobserver.positions.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.positions.dto.EmployeePositionDto
import uz.safecity.transportobserver.positions.service.EmployeePositionService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/positions")
class EmployeePositionController(
	private val employeePositionService: EmployeePositionService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping
	fun listAll(): ResponseEntity<ApiResponse<List<EmployeePositionDto>>> =
		ResponseEntity.ok(ApiResponse.ok(employeePositionService.listAll()))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<EmployeePositionDto>> =
		ResponseEntity.ok(ApiResponse.ok(employeePositionService.getById(id)))
}

package uz.safecity.transportobserver.positions.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.positions.dto.CreateEmployeePositionRequest
import uz.safecity.transportobserver.positions.dto.EmployeePositionDto
import uz.safecity.transportobserver.positions.dto.UpdateEmployeePositionRequest
import uz.safecity.transportobserver.positions.service.EmployeePositionService
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
@RequestMapping("/api/v1/admin/positions")
class AdminEmployeePositionController(
	private val employeePositionService: EmployeePositionService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping
	fun create(@Valid @RequestBody request: CreateEmployeePositionRequest): ResponseEntity<ApiResponse<EmployeePositionDto>> =
		ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(employeePositionService.create(request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PutMapping("/{id}")
	fun update(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateEmployeePositionRequest
	): ResponseEntity<ApiResponse<EmployeePositionDto>> =
		ResponseEntity.ok(ApiResponse.ok(employeePositionService.update(id, request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@DeleteMapping("/{id}")
	fun delete(@PathVariable id: UUID): ResponseEntity<ApiResponse<Boolean>> {
		employeePositionService.delete(id)
		return ResponseEntity.ok(ApiResponse.ok(true))
	}
}

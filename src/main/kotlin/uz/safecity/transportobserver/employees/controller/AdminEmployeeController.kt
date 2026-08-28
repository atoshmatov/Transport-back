package uz.safecity.transportobserver.employees.controller

import uz.safecity.transportobserver.auth.dto.ResetPasswordResponse
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.employees.dto.CreateEmployeeRequest
import uz.safecity.transportobserver.employees.dto.CreateEmployeeResponse
import uz.safecity.transportobserver.employees.dto.EmployeeDto
import uz.safecity.transportobserver.employees.dto.EmployeePositionHistoryDto
import uz.safecity.transportobserver.employees.dto.UpdateEmployeeRequest
import uz.safecity.transportobserver.employees.dto.UpdateEmployeeStatusRequest
import uz.safecity.transportobserver.employees.service.EmployeeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Admin-exclusive employee management (create/update/status/reset-password) — split out
 * of [EmployeeController] onto the `/api/v1/admin/employees` namespace so that admin-only
 * write endpoints are not mixed under the same base path as the SUPER_ADMIN/ADMIN/OPERATOR
 * read endpoints ([EmployeeController.list]/[EmployeeController.getById]).
 *
 * Every endpoint here is SUPER_ADMIN/ADMIN only (OPERATOR and INSPECTOR excluded) —
 * see each method's original kdoc history in EmployeeController for the reasoning.
 * Delegates to the same [EmployeeService], including its `assertCanManageRole` checks —
 * only the routing/URL prefix changed here, no service-layer behavior.
 */
@RestController
@RequestMapping("/api/v1/admin/employees")
class AdminEmployeeController(
	private val employeeService: EmployeeService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping
	fun create(
		@Valid @RequestBody request: CreateEmployeeRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<CreateEmployeeResponse>> =
		ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(employeeService.create(request, principal.accountId, principal.role)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PutMapping("/{id}")
	fun update(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateEmployeeRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<EmployeeDto>> =
		ResponseEntity.ok(ApiResponse.ok(employeeService.update(id, request, principal.accountId, principal.role)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PatchMapping("/{id}/status")
	fun updateStatus(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateEmployeeStatusRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<EmployeeDto>> =
		ResponseEntity.ok(
			ApiResponse.ok(
				employeeService.updateStatus(id, requireNotNull(request.isActive), principal.accountId, principal.role)
			)
		)

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping("/{id}/reset-password")
	fun resetPassword(
		@PathVariable id: UUID,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<ResetPasswordResponse>> =
		ResponseEntity.ok(ApiResponse.ok(employeeService.resetPassword(id, principal.accountId, principal.role)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping(value = ["/{id}/photo"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun uploadPhoto(
		@PathVariable id: UUID,
		@RequestParam("file") file: MultipartFile,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<EmployeeDto>> =
		ResponseEntity.ok(ApiResponse.ok(employeeService.uploadPhoto(id, file, principal.accountId, principal.role)))

	/** Lavozim/hudud o'zgarish jurnali — see [uz.safecity.transportobserver.employees.entity.EmployeePositionHistory] kdoc. */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@GetMapping("/{id}/position-history")
	fun getPositionHistory(@PathVariable id: UUID): ResponseEntity<ApiResponse<List<EmployeePositionHistoryDto>>> =
		ResponseEntity.ok(ApiResponse.ok(employeeService.getPositionHistory(id)))
}

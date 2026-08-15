package uz.safecity.transportobserver.employees.controller

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.employees.dto.EmployeeDto
import uz.safecity.transportobserver.employees.entity.EmployeeStatus
import uz.safecity.transportobserver.employees.service.EmployeeService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Read-only employee roster (SUPER_ADMIN/ADMIN/OPERATOR). Admin-exclusive write
 * endpoints (create/update/status/reset-password) live on
 * [uz.safecity.transportobserver.employees.controller.AdminEmployeeController]
 * under `/api/v1/admin/employees` — see that controller's kdoc for the split rationale.
 */
@RestController
@RequestMapping("/api/v1/employees")
class EmployeeController(
	private val employeeService: EmployeeService
) {

	// TZ 2-bo'lim (ruxsatlar matritsasi): full employee roster is an
	// Admin/Operator capability ("Xaritada barcha inspektorlarni ko'rish" etc.) —
	// INSPECTOR must not be able to list/read every other employee via this endpoint.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping
	fun list(
		@RequestParam(required = false) regionName: String?,
		@RequestParam(required = false) role: RoleType?,
		@RequestParam(required = false) isActive: Boolean?,
		@RequestParam(required = false) status: EmployeeStatus?,
		@PageableDefault(size = 20) pageable: Pageable
	): ResponseEntity<ApiResponse<PageResponse<EmployeeDto>>> =
		ResponseEntity.ok(ApiResponse.ok(employeeService.list(regionName, role, isActive, status, pageable)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<EmployeeDto>> =
		ResponseEntity.ok(ApiResponse.ok(employeeService.getById(id)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@PostMapping(value = ["/me/photo"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun uploadMyPhoto(
		@RequestParam("file") file: MultipartFile,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<EmployeeDto>> =
		ResponseEntity.ok(ApiResponse.ok(employeeService.uploadMyPhoto(principal.accountId, file)))
}

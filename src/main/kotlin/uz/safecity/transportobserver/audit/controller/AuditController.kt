package uz.safecity.transportobserver.audit.controller

import uz.safecity.transportobserver.audit.entity.AuditLog
import uz.safecity.transportobserver.audit.service.AuditService
import uz.safecity.transportobserver.common.dto.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// Every endpoint here is SUPER_ADMIN/ADMIN only (OPERATOR and INSPECTOR excluded) —
// audit-log visibility matches the same admin-exclusive shape as employee/checkpoint/vehicle
// writes, so the whole controller lives on the `/api/v1/admin/...` namespace.
@RestController
@RequestMapping("/api/v1/admin/audit")
class AuditController(
	private val auditService: AuditService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@GetMapping("/logs")
	fun list(): ResponseEntity<ApiResponse<List<AuditLog>>> =
		ResponseEntity.ok(ApiResponse.ok(auditService.list()))
}

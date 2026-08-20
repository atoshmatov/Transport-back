package uz.safecity.transportobserver.checkpointtypes.controller

import uz.safecity.transportobserver.checkpointtypes.dto.CheckpointTypeDto
import uz.safecity.transportobserver.checkpointtypes.service.CheckpointTypeService
import uz.safecity.transportobserver.common.dto.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Read-only checkpoint-type roster, open to every authenticated role (needed to
 * populate the "type" dropdown when SUPER_ADMIN/ADMIN create/edit a checkpoint —
 * see [uz.safecity.transportobserver.checkpoints.controller.AdminCheckpointController] —
 * as well as for OPERATOR/INSPECTOR read views that display a checkpoint's type name).
 * Admin-exclusive write endpoints (create/update/delete) live on
 * [AdminCheckpointTypeController] under `/api/v1/admin/checkpoint-types` — same
 * controller-splitting convention as [uz.safecity.transportobserver.positions.controller.EmployeePositionController]
 * / [uz.safecity.transportobserver.positions.controller.AdminEmployeePositionController].
 */
@RestController
@RequestMapping("/api/v1/checkpoint-types")
class CheckpointTypeController(
	private val checkpointTypeService: CheckpointTypeService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping
	fun listAll(): ResponseEntity<ApiResponse<List<CheckpointTypeDto>>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointTypeService.listAll()))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<CheckpointTypeDto>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointTypeService.getById(id)))
}

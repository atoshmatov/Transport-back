package uz.safecity.transportobserver.checkpoints.controller

import uz.safecity.transportobserver.checkpoints.dto.CheckpointDto
import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.dto.UpdateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.dto.UpdateCheckpointStatusRequest
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.common.dto.ApiResponse
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
 * Admin-exclusive checkpoint management (create/update/status) — split out of
 * [CheckpointController] onto the `/api/v1/admin/checkpoints` namespace so that admin-only
 * write endpoints are not mixed under the same base path as the SUPER_ADMIN/ADMIN/OPERATOR
 * read endpoints ([CheckpointController.list]/[CheckpointController.getById]).
 *
 * Every endpoint here is SUPER_ADMIN/ADMIN only (OPERATOR and INSPECTOR excluded).
 * Delegates to the same [CheckpointService] — only the routing/URL prefix changed here,
 * no service-layer behavior.
 */
@RestController
@RequestMapping("/api/v1/admin/checkpoints")
class AdminCheckpointController(
	private val checkpointService: CheckpointService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping
	fun create(@Valid @RequestBody request: CreateCheckpointRequest): ResponseEntity<ApiResponse<CheckpointDto>> =
		ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(checkpointService.create(request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PutMapping("/{id}")
	fun update(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateCheckpointRequest
	): ResponseEntity<ApiResponse<CheckpointDto>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.update(id, request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PatchMapping("/{id}/status")
	fun updateStatus(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateCheckpointStatusRequest
	): ResponseEntity<ApiResponse<CheckpointDto>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.updateStatus(id, requireNotNull(request.isActive))))
}

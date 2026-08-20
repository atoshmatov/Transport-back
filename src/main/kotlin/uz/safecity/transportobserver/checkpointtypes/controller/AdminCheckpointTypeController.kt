package uz.safecity.transportobserver.checkpointtypes.controller

import uz.safecity.transportobserver.checkpointtypes.dto.CheckpointTypeDto
import uz.safecity.transportobserver.checkpointtypes.dto.CreateCheckpointTypeRequest
import uz.safecity.transportobserver.checkpointtypes.dto.UpdateCheckpointTypeRequest
import uz.safecity.transportobserver.checkpointtypes.service.CheckpointTypeService
import uz.safecity.transportobserver.common.dto.ApiResponse
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

/** SUPER_ADMIN/ADMIN-only checkpoint-type management — see [CheckpointTypeController] kdoc for the split rationale. */
@RestController
@RequestMapping("/api/v1/admin/checkpoint-types")
class AdminCheckpointTypeController(
	private val checkpointTypeService: CheckpointTypeService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping
	fun create(@Valid @RequestBody request: CreateCheckpointTypeRequest): ResponseEntity<ApiResponse<CheckpointTypeDto>> =
		ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(checkpointTypeService.create(request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PutMapping("/{id}")
	fun update(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateCheckpointTypeRequest
	): ResponseEntity<ApiResponse<CheckpointTypeDto>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointTypeService.update(id, request)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@DeleteMapping("/{id}")
	fun delete(@PathVariable id: UUID): ResponseEntity<ApiResponse<Boolean>> {
		checkpointTypeService.delete(id)
		return ResponseEntity.ok(ApiResponse.ok(true))
	}
}

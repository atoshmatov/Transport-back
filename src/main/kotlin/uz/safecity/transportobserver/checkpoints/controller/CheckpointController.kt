package uz.safecity.transportobserver.checkpoints.controller

import uz.safecity.transportobserver.checkpoints.dto.CheckpointDto
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.common.dto.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Admin-section checkpoint roster, read-only (TZ section 6, "Nazorat/transport"),
 * open to SUPER_ADMIN/ADMIN/OPERATOR. Admin-exclusive write endpoints
 * (create/update/status) live on
 * [uz.safecity.transportobserver.checkpoints.controller.AdminCheckpointController]
 * under `/api/v1/admin/checkpoints` — see that controller's kdoc for the split rationale.
 *
 * The public map view is served separately by
 * [uz.safecity.transportobserver.map.controller.MapController] (`GET /api/v1/map/checkpoints`),
 * which is open to every authenticated role, including INSPECTOR — see that
 * controller's kdoc for why this module is split across multiple controllers.
 */
@RestController
@RequestMapping("/api/v1/checkpoints")
class CheckpointController(
	private val checkpointService: CheckpointService
) {

	// TZ 5.6-bo'lim (ruxsatlar matritsasi): the full checkpoint roster is an
	// Admin/Operator capability — INSPECTOR must not list every checkpoint via
	// this endpoint (same pattern as EmployeeController.list).
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping
	fun list(
		@RequestParam(required = false) regionName: String?,
		@RequestParam(required = false) type: String?,
		@RequestParam(required = false) isActive: Boolean?,
		@RequestParam(required = false) checkpointTypeId: UUID?,
		@PageableDefault(size = 20) pageable: Pageable
	): ResponseEntity<ApiResponse<PageResponse<CheckpointDto>>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.list(regionName, type, isActive, checkpointTypeId, pageable)))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<CheckpointDto>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.getById(id)))
}

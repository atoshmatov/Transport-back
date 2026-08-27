package uz.safecity.transportobserver.inspections.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.inspections.dto.CreateInspectionRequest
import uz.safecity.transportobserver.inspections.dto.InspectionDetailDto
import uz.safecity.transportobserver.inspections.dto.InspectionDto
import uz.safecity.transportobserver.inspections.dto.UpdateInspectionStatusRequest
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.service.InspectionService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Planned, admin/operator-assigned inspection tasks (TZ section 6) — see
 * [uz.safecity.transportobserver.inspections.entity.Inspection] kdoc for how
 * this differs from [uz.safecity.transportobserver.incidents.entity.Incident] /
 * [uz.safecity.transportobserver.incidents.controller.IncidentController], whose
 * role-gating this controller mirrors.
 */
@RestController
@RequestMapping("/api/v1/inspections")
class InspectionController(
	private val inspectionService: InspectionService
) {

	// All 4 roles may call list/getById: INSPECTOR is scoped down to its own assigned
	// inspections inside InspectionService, not blocked from the endpoint entirely — same
	// "web/mobile access for inspectors" shape as IncidentController.list.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping
	fun list(
		@RequestParam(required = false) status: InspectionStatus?,
		@RequestParam(required = false) checkpointId: UUID?,
		@RequestParam(required = false) assignedInspectorId: UUID?,
		@PageableDefault(size = 20) pageable: Pageable,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<PageResponse<InspectionDto>>> =
		ResponseEntity.ok(
			ApiResponse.ok(inspectionService.list(status, checkpointId, assignedInspectorId, principal, pageable))
		)

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}")
	fun getById(
		@PathVariable id: UUID,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<InspectionDetailDto>> =
		ResponseEntity.ok(ApiResponse.ok(inspectionService.getById(id, principal)))

	// Scheduling a new inspection is an admin/dispatch action, not something an inspector does
	// to themselves — INSPECTOR deliberately excluded, same as IncidentController.assignInspector.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@PostMapping
	fun create(
		@Valid @RequestBody request: CreateInspectionRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<InspectionDto>> =
		ResponseEntity.status(HttpStatus.CREATED).body(
			ApiResponse.ok(inspectionService.create(request, principal.accountId, principal.role))
		)

	// Unlike POST, INSPECTOR IS allowed here — a field inspector updating their own assigned
	// task's status (e.g. "boshlandi" / "yakunlandi"). InspectionService#updateStatus scopes
	// that case down to the caller's own assigned inspection (404 on a foreign id, never 403 —
	// see its kdoc); SUPER_ADMIN/ADMIN/OPERATOR may target any inspection.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@PatchMapping("/{id}/status")
	fun updateStatus(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateInspectionStatusRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<InspectionDto>> =
		ResponseEntity.ok(
			ApiResponse.ok(
				inspectionService.updateStatus(
					id,
					requireNotNull(request.status),
					request.notes,
					principal.accountId,
					principal.role,
					request.checklistItems,
					request.driverConfirmed,
					request.witnessName
				)
			)
		)
}

package uz.safecity.transportobserver.incidents.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.incidents.dto.AssignInspectorRequest
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.dto.EvidenceDto
import uz.safecity.transportobserver.incidents.dto.IncidentDetailDto
import uz.safecity.transportobserver.incidents.dto.IncidentDto
import uz.safecity.transportobserver.incidents.dto.UpdateIncidentResolutionRequest
import uz.safecity.transportobserver.incidents.dto.UpdateIncidentStatusRequest
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.service.EvidenceService
import uz.safecity.transportobserver.incidents.service.IncidentService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/incidents")
class IncidentController(
	private val incidentService: IncidentService,
	private val evidenceService: EvidenceService
) {

	// The mobile app's primary write path (TZ sections 7/8) — INSPECTOR is deliberately
	// included (unlike /assign): a field inspector reports their own incident. OPERATOR is
	// also allowed for manual/dispatch entry (TZ permission matrix: "Hodisa qayd etish —
	// qo'lda" for Operator). See IncidentService#create kdoc for the clientUuid dedup and
	// auto-assign-to-self-when-INSPECTOR rules.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@PostMapping
	fun create(
		@Valid @RequestBody request: CreateIncidentRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<IncidentDto>> =
		ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(incidentService.create(request, principal)))

	// All 4 roles may call list/getById: INSPECTOR is scoped down to its own
	// assigned incidents inside IncidentService, it is not blocked from the
	// endpoint entirely (that's the "web access for inspectors" requirement —
	// see IncidentService kdoc for the scoping rule itself). [assignedInspectorId]
	// is only meaningful for SUPER_ADMIN/ADMIN/OPERATOR callers — see
	// IncidentService#list kdoc for why an INSPECTOR caller can't use it to widen
	// their own view.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping
	fun list(
		@RequestParam(required = false) status: IncidentStatus?,
		@RequestParam(required = false) type: IncidentType?,
		@RequestParam(required = false) assignedInspectorId: UUID?,
		@PageableDefault(size = 20) pageable: Pageable,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<PageResponse<IncidentDto>>> =
		ResponseEntity.ok(
			ApiResponse.ok(incidentService.list(status, type, assignedInspectorId, principal, pageable))
		)

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}")
	fun getById(
		@PathVariable id: UUID,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<IncidentDetailDto>> =
		ResponseEntity.ok(ApiResponse.ok(incidentService.getById(id, principal)))

	// Assignment is an admin/dispatch action, not something an inspector does
	// to themselves — INSPECTOR deliberately excluded here.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@PatchMapping("/{id}/assign")
	fun assignInspector(
		@PathVariable id: UUID,
		@Valid @RequestBody request: AssignInspectorRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<IncidentDto>> =
		ResponseEntity.ok(
			ApiResponse.ok(
				incidentService.assignInspector(
					id,
					requireNotNull(request.inspectorAccountId),
					principal.accountId,
					principal.role
				)
			)
		)

	// "Ko'rilgan chora" (resolution) — a dispatch/back-office action, same 3 roles as /assign.
	// INSPECTOR deliberately excluded (unlike /status): recording a resolution's note/fine/deadline/
	// responsible person is not something a field inspector does to their own case — see
	// IncidentService#updateResolution kdoc.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@PatchMapping("/{id}/resolution")
	fun updateResolution(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateIncidentResolutionRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<IncidentDto>> =
		ResponseEntity.ok(ApiResponse.ok(incidentService.updateResolution(id, request, principal)))

	// Unlike /assign, INSPECTOR IS allowed here — a field inspector marking their own
	// assigned incident's status (e.g. "yakunlandi"). IncidentService#updateStatus scopes
	// that case down to the caller's own assigned incident (404 on a foreign incident id,
	// never 403 — see its kdoc); SUPER_ADMIN/ADMIN/OPERATOR may target any incident.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@PatchMapping("/{id}/status")
	fun updateStatus(
		@PathVariable id: UUID,
		@Valid @RequestBody request: UpdateIncidentStatusRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<IncidentDto>> =
		ResponseEntity.ok(
			ApiResponse.ok(
				incidentService.updateStatus(
					id,
					requireNotNull(request.status),
					principal.accountId,
					principal.role
				)
			)
		)

	// Same 4 roles as create/status: an INSPECTOR attaches photo evidence to their own
	// assigned incident; SUPER_ADMIN/ADMIN/OPERATOR may attach to any. EvidenceService applies
	// the same ownership scoping as IncidentService (404 on a foreign incident id — see its
	// kdoc), plus MIME/size validation (TZ section 9).
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@PostMapping("/{id}/evidence", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun uploadEvidence(
		@PathVariable id: UUID,
		@RequestParam("file") file: MultipartFile,
		@RequestParam(required = false) capturedAt: Instant?,
		@RequestParam(required = false) latitude: Double?,
		@RequestParam(required = false) longitude: Double?,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<EvidenceDto>> =
		ResponseEntity.status(HttpStatus.CREATED).body(
			ApiResponse.ok(evidenceService.upload(id, file, capturedAt, latitude, longitude, principal))
		)

	// Gallery read for an incident's evidence — same ownership scoping as everything else in
	// this controller (see EvidenceService#findAccessible kdoc).
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/{id}/evidence")
	fun listEvidence(
		@PathVariable id: UUID,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<List<EvidenceDto>>> =
		ResponseEntity.ok(ApiResponse.ok(evidenceService.list(id, principal)))
}

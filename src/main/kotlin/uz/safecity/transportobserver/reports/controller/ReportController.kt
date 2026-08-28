package uz.safecity.transportobserver.reports.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.reports.dto.CreateReportRequest
import uz.safecity.transportobserver.reports.dto.ReportDownloadDto
import uz.safecity.transportobserver.reports.dto.ReportDto
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.service.ReportService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Generated-report-FILE listing + export (TZ section 7): `GET /reports`, `GET /reports/{id}`
 * (pre-existing, plain CRUD-read) plus the async export pipeline added on top —
 * `POST /reports` (queues a PDF build, see [ReportService.create]) and
 * `GET /reports/{id}/download` (mints a signed MinIO URL once ready, see
 * [ReportService.getDownloadUrl]). SUPER_ADMIN/ADMIN/OPERATOR only on all four endpoints, matching
 * [uz.safecity.transportobserver.reports.controller.ReportStatsController]'s `dashboard` role set
 * for the same TZ section 7 API group — a generated report document (who requested it, its
 * period/type, its file) is dispatch/management data, not something a field INSPECTOR should be
 * able to list or read. `list`/`getById` return [uz.safecity.transportobserver.reports.dto.ReportDto]
 * rather than the raw [Report] entity — see that DTO's kdoc for exactly which entity fields are
 * deliberately left off the wire.
 */
@RestController
@RequestMapping("/api/v1/reports")
class ReportController(
	private val reportService: ReportService
) {

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping
	fun list(): ResponseEntity<ApiResponse<List<ReportDto>>> =
		ResponseEntity.ok(ApiResponse.ok(reportService.list()))

	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<ReportDto>> =
		ResponseEntity.ok(ApiResponse.ok(reportService.getById(id)))

	/**
	 * Queues a new report for async generation and returns immediately — the returned [Report]'s
	 * `status` is always PENDING; poll `GET /reports/{id}` (or `download`) afterward to see it
	 * progress to GENERATING/READY/FAILED. See [ReportService.create] kdoc for the RabbitMQ handoff.
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@PostMapping
	fun create(
		@Valid @RequestBody request: CreateReportRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<Report>> =
		ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(reportService.create(request, principal.accountId)))

	/**
	 * Returns a short-lived signed MinIO URL once the report is READY. A [Report] that is still
	 * PENDING/GENERATING, or that FAILED, produces a 409 (see [ReportService.getDownloadUrl] kdoc)
	 * carrying the current status so the caller knows whether to keep polling or give up.
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/{id}/download")
	fun download(@PathVariable id: UUID): ResponseEntity<ApiResponse<ReportDownloadDto>> =
		ResponseEntity.ok(ApiResponse.ok(ReportDownloadDto(url = reportService.getDownloadUrl(id), expiresInMinutes = 15)))
}

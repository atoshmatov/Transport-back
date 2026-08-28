package uz.safecity.transportobserver.reports.dto

import uz.safecity.transportobserver.reports.entity.ReportType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * `POST /api/v1/reports` request body — kicks off the async PDF-export pipeline (see
 * [uz.safecity.transportobserver.reports.service.ReportService.create] kdoc). [periodStart]/
 * [periodEnd] scope which domain rows ([type]-dependent) land in the generated document; both are
 * required (unlike e.g. [uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest]'s
 * optional geo fields) since a report with no period boundary would silently mean "everything,
 * all-time", which is a decision the caller should make explicitly, not one the backend defaults to.
 */
data class CreateReportRequest(
	@field:NotBlank(message = "title majburiy")
	@field:Size(max = 255, message = "title 255 belgidan oshmasligi kerak")
	val title: String,

	@field:NotNull(message = "type majburiy")
	val type: ReportType?,

	@field:NotNull(message = "periodStart majburiy")
	val periodStart: Instant?,

	@field:NotNull(message = "periodEnd majburiy")
	val periodEnd: Instant?
)

/** `GET /api/v1/reports/{id}/download` response — a short-lived signed MinIO GET URL, same shape/reasoning as [uz.safecity.transportobserver.incidents.dto.EvidenceDto]'s presigned URL. */
data class ReportDownloadDto(
	val url: String,
	val expiresInMinutes: Int
)

/**
 * RabbitMQ payload published on [uz.safecity.transportobserver.common.config.RabbitMQConfig.ROUTING_KEY_REPORT_GENERATION]
 * by [uz.safecity.transportobserver.reports.service.ReportService.create] and consumed by
 * [uz.safecity.transportobserver.reports.service.ReportGenerationListener]. Deliberately carries
 * only the id — the listener re-reads the [uz.safecity.transportobserver.reports.entity.Report] row
 * fresh from the DB rather than trusting a snapshot that could go stale between publish and consume.
 */
data class ReportGenerationMessage(
	val reportId: UUID
)

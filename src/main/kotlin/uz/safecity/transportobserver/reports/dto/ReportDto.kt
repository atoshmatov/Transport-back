package uz.safecity.transportobserver.reports.dto

import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.entity.ReportStatus
import uz.safecity.transportobserver.reports.entity.ReportType
import java.time.Instant
import java.util.UUID

/**
 * `GET /api/v1/reports` (list) and `GET /api/v1/reports/{id}` response shape — a safe projection of
 * [Report] for [uz.safecity.transportobserver.reports.controller.ReportController]'s two read
 * endpoints. Deliberately drops two [Report] fields from the wire:
 * - [Report.fileUrl]: the raw MinIO object key, never meant for direct client use — only
 *   `GET /reports/{id}/download` (see [ReportDownloadDto]) should hand out a value derived from it,
 *   and only as a short-lived signed URL via [uz.safecity.transportobserver.common.storage.FileStorageService.presignedGetUrl].
 * - [Report.errorMessage]: a raw exception message that can carry internal implementation details
 *   (see [uz.safecity.transportobserver.reports.service.ReportGenerationService] kdoc for what sets
 *   it) — [status] = FAILED already tells the caller generation failed, which is all a normal
 *   SUPER_ADMIN/ADMIN/OPERATOR caller needs from this list/detail view.
 */
data class ReportDto(
	val id: UUID,
	val title: String,
	val type: ReportType,
	val status: ReportStatus,
	val periodStart: Instant?,
	val periodEnd: Instant?,
	val generatedBy: UUID?,
	val createdAt: Instant?,
	val updatedAt: Instant?
) {
	companion object {
		fun from(report: Report) = ReportDto(
			id = requireNotNull(report.id),
			title = report.title,
			type = report.type,
			status = report.status,
			periodStart = report.periodStart,
			periodEnd = report.periodEnd,
			generatedBy = report.generatedBy,
			createdAt = report.createdAt,
			updatedAt = report.updatedAt
		)
	}
}

package uz.safecity.transportobserver.reports.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class ReportStatus { PENDING, GENERATING, READY, FAILED }
enum class ReportType { INCIDENTS_SUMMARY, EMPLOYEE_ACTIVITY, RAILSAFE_EVENTS, CUSTOM }

/**
 * Async PDF-export pipeline (TZ section 7 reports API group): `POST /reports` saves this row as
 * PENDING and publishes a `report.generated` RabbitMQ message carrying its id; `ReportGenerationListener`
 * consumes it, flips to GENERATING, renders an HTML->PDF via openhtmltopdf, uploads to MinIO, and
 * flips to READY (with [fileUrl] set) or FAILED (with [errorMessage] set) — see
 * [uz.safecity.transportobserver.reports.service.ReportGenerationService] kdoc for the full flow.
 */
@Entity
@Table(name = "reports")
class Report(

	@Column(nullable = false)
	var title: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var type: ReportType,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var status: ReportStatus = ReportStatus.PENDING,

	@Column(name = "period_start")
	var periodStart: Instant? = null,

	@Column(name = "period_end")
	var periodEnd: Instant? = null,

	@Column(name = "generated_by")
	var generatedBy: UUID? = null,

	/** MinIO object URL/key once status = READY. */
	@Column(name = "file_url")
	var fileUrl: String? = null,

	/** Failure reason (exception message) when [status] = FAILED — see ReportGenerationService#generate. Null otherwise. */
	@Column(name = "error_message", columnDefinition = "text")
	var errorMessage: String? = null

) : BaseEntity()

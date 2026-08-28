package uz.safecity.transportobserver.reports.service

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import uz.safecity.transportobserver.common.storage.FileStorageService
import uz.safecity.transportobserver.employees.entity.EmployeeStatus
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.railsafe.entity.RailEventSeverity
import uz.safecity.transportobserver.railsafe.repository.RailCrossingEventRepository
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.entity.ReportStatus
import uz.safecity.transportobserver.reports.entity.ReportType
import uz.safecity.transportobserver.reports.repository.ReportRepository
import uz.safecity.transportobserver.shifts.repository.WorkShiftRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The actual PDF-export work behind `POST /reports` (see [ReportService.create] kdoc for the
 * async handoff) — invoked by [ReportGenerationListener] off a RabbitMQ consumer thread, NOT a web
 * request thread, so this is free to take as long as it needs.
 *
 * [generate] is deliberately callable directly (plain method call, no broker involved) — that is
 * how this class's own test suite exercises it, and how [ReportGenerationListener] invokes it too.
 * Kept as its own class (rather than folded into [ReportService]) so the "read domain data ->
 * render HTML -> PDF -> upload -> flip status" pipeline is testable/reasoned-about independently
 * of the `POST /reports` request/response path.
 *
 * Each DB write below goes through a *separate* `reportRepository.save(...)` call rather than one
 * long `@Transactional` method spanning the whole render+upload — `save()` on a Spring Data JPA
 * repository already opens/commits its own short transaction, so the slow HTML->PDF render and
 * MinIO upload in between never hold a DB connection/transaction open.
 */
@Service
class ReportGenerationService(
	private val reportRepository: ReportRepository,
	private val incidentRepository: IncidentRepository,
	private val employeeRepository: EmployeeRepository,
	private val inspectionRepository: InspectionRepository,
	private val workShiftRepository: WorkShiftRepository,
	private val railCrossingEventRepository: RailCrossingEventRepository,
	private val fileStorageService: FileStorageService
) {

	private val log = LoggerFactory.getLogger(ReportGenerationService::class.java)

	/**
	 * PENDING -> GENERATING -> (READY with [Report.fileUrl] set) or (FAILED with
	 * [Report.errorMessage] set). A missing [reportId] (row deleted between publish and consume —
	 * not expected in practice, but not impossible) is logged and skipped rather than thrown: there
	 * is nothing left to mark FAILED, and retrying a RabbitMQ delivery for a row that will never
	 * exist again would just loop forever under the default listener retry policy.
	 *
	 * Idempotency guard: RabbitMQ only guarantees *at-least-once* delivery, so
	 * [ReportGenerationListener.onMessage] can be invoked more than once for the same message (e.g.
	 * a redelivery after a broker-side ack timeout, or the bounded retry described below). Only a
	 * report still sitting at [ReportStatus.PENDING] is (re-)processed here — one already READY or
	 * FAILED (or mid-flight GENERATING from a concurrent/earlier delivery) is left untouched instead
	 * of being re-rendered/re-uploaded and its terminal status clobbered back to GENERATING.
	 *
	 * The ENTIRE body is wrapped in one outer try-catch (including the initial [reportRepository]
	 * `findById` and the PENDING -> GENERATING `save()`, not just the render/upload/READY section
	 * below) so that NO exception — including a transient one from those two DB calls, e.g. a
	 * connection-pool timeout — ever escapes this method back into
	 * [ReportGenerationListener.onMessage]. Spring AMQP's default listener behaviour on an
	 * uncaught exception is to requeue the message and redeliver it immediately, which for a
	 * still-ongoing transient failure becomes an unbounded, CPU-spinning poison-message loop.
	 * [uz.safecity.transportobserver.common.config.RabbitMQConfig] additionally configures a bounded
	 * `spring.rabbitmq.listener.simple.retry` policy plus a dead-letter queue as a second line of
	 * defense for anything that can still reach the listener from *outside* this method's control
	 * (message deserialization failures, or any future bug in this pipeline) — bounded retries, then
	 * parked in the DLQ for manual inspection/replay instead of looping forever.
	 *
	 * Every `reportRepository.save(...)` result below is reassigned back onto `report` rather than
	 * discarded. Each `save()` call opens/commits its *own* short transaction (see this class's
	 * kdoc), so the `report` instance is JPA-*detached* by the time the next `save()` runs — Hibernate's
	 * `merge()` (which `save()` calls for an already-persisted, i.e. non-new, entity) returns a new
	 * managed copy carrying the post-flush `@Version` value; it does NOT write that value back onto
	 * the detached instance passed in. Continuing to mutate/save the ORIGINAL (now version-stale)
	 * `report` reference on a subsequent call sends a stale version and throws
	 * `ObjectOptimisticLockingFailureException` on that second write — always, not just under
	 * contention — which is exactly what the FAILED-status save further below would otherwise hit
	 * immediately after a successful render+upload.
	 */
	fun generate(reportId: UUID) {
		try {
			var report = reportRepository.findById(reportId).orElse(null)
			if (report == null) {
				log.warn("Report {} not found — skipping generation", reportId)
				return
			}

			if (report.status != ReportStatus.PENDING) {
				log.info(
					"Report {} is already {} — skipping (redelivered or duplicate message)",
					reportId,
					report.status
				)
				return
			}

			report.status = ReportStatus.GENERATING
			report = reportRepository.save(report)

			try {
				val html = buildHtml(report)
				val pdfBytes = renderPdf(html)
				val objectKey = "reports/${report.id}/${sanitizeFileName(report.title)}.pdf"
				fileStorageService.upload(objectKey, pdfBytes, "application/pdf")

				report.status = ReportStatus.READY
				report.fileUrl = objectKey
				report.errorMessage = null
				reportRepository.save(report)
			} catch (ex: Exception) {
				log.error("Report {} generation failed", reportId, ex)
				report.status = ReportStatus.FAILED
				report.errorMessage = (ex.message ?: ex.javaClass.simpleName).take(MAX_ERROR_MESSAGE_LENGTH)
				reportRepository.save(report)
			}
		} catch (ex: Exception) {
			log.error(
				"Unexpected error generating report {} before/while marking it GENERATING — leaving it as-is " +
					"rather than rethrowing, to avoid an unbounded RabbitMQ redelivery loop",
				reportId,
				ex
			)
		}
	}

	private fun buildHtml(report: Report): String {
		val start = report.periodStart ?: Instant.EPOCH
		val end = report.periodEnd ?: Instant.now()
		val body = when (report.type) {
			ReportType.INCIDENTS_SUMMARY -> incidentsSummaryBody(start, end)
			ReportType.EMPLOYEE_ACTIVITY -> employeeActivityBody(start, end)
			ReportType.RAILSAFE_EVENTS -> railsafeEventsBody(start, end)
			// No dedicated data source defined for CUSTOM yet — TODO once a real "custom report
			// builder" requirement lands; a blank-but-valid PDF is generated rather than failing
			// the whole request, since CUSTOM is a legitimate, chosen report type, not an error.
			ReportType.CUSTOM -> "<p>Maxsus hisobot shabloni hozircha mavjud emas.</p>"
		}

		// NOTE: deliberately `trimMargin()` with an explicit "|" prefix, NOT `trimIndent()`.
		// [body] (incidentsSummaryBody/employeeActivityBody/railsafeEventsBody) is itself a
		// multi-line string that was ALREADY flattened flush-left by its own `.trimIndent()` call
		// before being spliced in here via `$body`. If this outer template also used
		// `.trimIndent()`, it would compute the common indent across EVERY line of the final
		// string INCLUDING those already-flush-left inner lines — dragging the common indent down
		// to zero and leaving this template's own tab-indentation (e.g. before the `<!DOCTYPE`
		// line) un-stripped, which broke [renderPdf]'s XML parsing (a non-whitespace-prefixed
		// document is required). `trimMargin()` only strips up to its own explicit "|" marker on
		// lines that carry one, so the unmarked lines coming from `$body` pass through untouched
		// regardless of their own indentation — no similar corruption. (Only [ReportType.CUSTOM],
		// whose body is a single unmarked line, was exercising the same string correctly and never
		// caught this — [INCIDENTS_SUMMARY]/[EMPLOYEE_ACTIVITY]/[RAILSAFE_EVENTS] would fail on
		// every call.)
		return """
			|<!DOCTYPE html>
			|<html xmlns="http://www.w3.org/1999/xhtml">
			|<head>
			|<meta charset="UTF-8" />
			|<title>${esc(report.title)}</title>
			|<style>
			|  body { font-family: sans-serif; font-size: 11px; color: #222222; }
			|  h1 { font-size: 18px; margin-bottom: 4px; }
			|  h2 { font-size: 13px; margin-top: 18px; margin-bottom: 6px; }
			|  .meta { color: #555555; margin-bottom: 16px; font-size: 10px; }
			|  table { width: 100%; border-collapse: collapse; margin-top: 4px; }
			|  th, td { border: 1px solid #999999; padding: 4px 6px; text-align: left; font-size: 10px; }
			|  th { background-color: #eeeeee; }
			|</style>
			|</head>
			|<body>
			|  <h1>${esc(report.title)}</h1>
			|  <div class="meta">
			|    Turi: ${esc(report.type.name)}<br/>
			|    Davr: ${fmtDate(start)} - ${fmtDate(end)}<br/>
			|    Yaratilgan: ${fmtDate(Instant.now())}
			|  </div>
			|  $body
			|</body>
			|</html>
		""".trimMargin()
	}

	private fun incidentsSummaryBody(start: Instant, end: Instant): String {
		val incidents = incidentRepository.findByCreatedAtBetween(start, end)
		val byType = incidents.groupingBy { it.type }.eachCount()

		val summaryRows = buildString {
			append("<tr><td>Jami hodisalar</td><td>${incidents.size}</td></tr>")
			IncidentType.entries.forEach { type ->
				append("<tr><td>${esc(type.name)}</td><td>${byType[type] ?: 0}</td></tr>")
			}
		}

		val shown = incidents.sortedByDescending { it.createdAt }.take(MAX_DETAIL_ROWS)
		val detailRows = shown.joinToString("") { incident ->
			"<tr><td>${esc(incident.title)}</td><td>${esc(incident.type.name)}</td>" +
				"<td>${esc(incident.status.name)}</td><td>${incident.createdAt?.let { fmtDate(it) } ?: "-"}</td></tr>"
		}
		val detailHeading = if (incidents.size > MAX_DETAIL_ROWS) {
			"Hodisalar ro'yxati (so'nggi $MAX_DETAIL_ROWS ta, jami ${incidents.size})"
		} else {
			"Hodisalar ro'yxati"
		}

		return """
			<h2>Umumiy statistika</h2>
			<table><tr><th>Ko'rsatkich</th><th>Soni</th></tr>$summaryRows</table>
			<h2>$detailHeading</h2>
			<table><tr><th>Sarlavha</th><th>Turi</th><th>Holati</th><th>Sana</th></tr>$detailRows</table>
		""".trimIndent()
	}

	private fun employeeActivityBody(start: Instant, end: Instant): String {
		val totalEmployees = employeeRepository.countByStatusNot(EmployeeStatus.DISMISSED)
		val inspectionsCreated = inspectionRepository.countByCreatedAtBetween(start, end)
		val inspectionsCompleted = inspectionRepository.countByStatusAndPerformedAtBetween(InspectionStatus.COMPLETED, start, end)
		val shiftsStarted = workShiftRepository.countByStartedAtBetween(start, end)

		return """
			<h2>Xodimlar faoliyati</h2>
			<table>
			  <tr><th>Ko'rsatkich</th><th>Qiymat</th></tr>
			  <tr><td>Jami xodimlar (ishdan bo'shatilmagan)</td><td>$totalEmployees</td></tr>
			  <tr><td>Davr ichida yaratilgan tekshiruvlar</td><td>$inspectionsCreated</td></tr>
			  <tr><td>Davr ichida yakunlangan tekshiruvlar</td><td>$inspectionsCompleted</td></tr>
			  <tr><td>Davr ichida boshlangan smenalar</td><td>$shiftsStarted</td></tr>
			</table>
		""".trimIndent()
	}

	private fun railsafeEventsBody(start: Instant, end: Instant): String {
		val events = railCrossingEventRepository.findByDetectedAtBetween(start, end)
		val bySeverity = events.groupingBy { it.severity }.eachCount()

		val summaryRows = buildString {
			append("<tr><td>Jami hodisalar</td><td>${events.size}</td></tr>")
			RailEventSeverity.entries.forEach { severity ->
				append("<tr><td>${esc(severity.name)}</td><td>${bySeverity[severity] ?: 0}</td></tr>")
			}
		}

		val shown = events.sortedByDescending { it.detectedAt }.take(MAX_DETAIL_ROWS)
		val detailRows = shown.joinToString("") { event ->
			"<tr><td>${esc(event.crossingCode)}</td><td>${esc(event.eventType.name)}</td>" +
				"<td>${esc(event.severity.name)}</td><td>${fmtDate(event.detectedAt)}</td></tr>"
		}

		return """
			<h2>Umumiy statistika</h2>
			<table><tr><th>Ko'rsatkich</th><th>Soni</th></tr>$summaryRows</table>
			<h2>Hodisalar ro'yxati</h2>
			<table><tr><th>Kesishma</th><th>Turi</th><th>Darajasi</th><th>Sana</th></tr>$detailRows</table>
		""".trimIndent()
	}

	private fun renderPdf(html: String): ByteArray {
		val output = ByteArrayOutputStream()
		val builder = PdfRendererBuilder()
		builder.useFastMode()
		builder.withHtmlContent(html, "")
		builder.toStream(output)
		builder.run()
		return output.toByteArray()
	}

	/** MinIO object key must not contain characters that break URL/path handling — collapse anything else to `_`. */
	private fun sanitizeFileName(title: String): String {
		val cleaned = title.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_")
		return cleaned.ifBlank { "report" }.take(100)
	}

	/** Minimal HTML-entity escaping — every piece of user-supplied text (report title, incident title, ...) must go through this before being spliced into the XHTML template. */
	private fun esc(value: String?): String =
		(value ?: "")
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;")

	private fun fmtDate(instant: Instant): String = DISPLAY_FORMATTER.format(instant)

	companion object {
		/** Caps how many detail rows land in the PDF body — a report spanning years of data must not produce an unbounded/huge PDF. Summary counts above the table are always complete, unaffected by this cap. */
		private const val MAX_DETAIL_ROWS = 200
		private const val MAX_ERROR_MESSAGE_LENGTH = 500
		private val DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Tashkent"))
	}
}

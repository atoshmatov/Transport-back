package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.common.config.RabbitMQConfig
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.common.storage.FileStorageService
import uz.safecity.transportobserver.reports.dto.CreateReportRequest
import uz.safecity.transportobserver.reports.dto.ReportDto
import uz.safecity.transportobserver.reports.dto.ReportGenerationMessage
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.entity.ReportStatus
import uz.safecity.transportobserver.reports.repository.ReportRepository
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Generated-report-FILE lifecycle: `POST /reports` (this class) saves a PENDING [Report] row and
 * hands off the actual PDF build to [ReportGenerationService] asynchronously via RabbitMQ — see
 * [uz.safecity.transportobserver.common.config.RabbitMQConfig.QUEUE_REPORT_GENERATION] kdoc. Kept
 * separate from [ReportGenerationService] (which owns the CPU/IO-heavy HTML->PDF render + MinIO
 * upload, invoked off the RabbitMQ listener thread, not a web request thread) so a slow/failed
 * generation never blocks the `POST /reports` response — the whole point of making this async in
 * the first place.
 */
@Service
class ReportService(
	private val reportRepository: ReportRepository,
	private val rabbitTemplate: RabbitTemplate,
	private val fileStorageService: FileStorageService
) {

	/** `GET /api/v1/reports` — see [ReportDto] kdoc for why this is a projection rather than the raw [Report] entity. */
	fun list(): List<ReportDto> = reportRepository.findAll().map { ReportDto.from(it) }

	/** `GET /api/v1/reports/{id}` — see [ReportDto] kdoc for why this is a projection rather than the raw [Report] entity. */
	fun getById(id: UUID): ReportDto = ReportDto.from(findOrThrow(id))

	private fun findOrThrow(id: UUID): Report =
		reportRepository.findById(id).orElseThrow { ResourceNotFoundException("error.report.not-found", id) }

	/**
	 * Saves the PENDING row first, then publishes — in that order, on purpose: if the publish
	 * ever fails (broker down), the caller still gets back a real PENDING [Report] row rather than
	 * nothing at all; a stuck-PENDING report is a visible, debuggable state (unlike silently losing
	 * the request), and nothing here currently retries a failed publish (TODO: an outbox pattern is
	 * the correct fix if this proves to be a real gap in practice, not a speculative one today).
	 */
	@Transactional
	fun create(request: CreateReportRequest, generatedBy: UUID?): Report {
		val saved = reportRepository.save(
			Report(
				title = request.title,
				type = requireNotNull(request.type),
				status = ReportStatus.PENDING,
				periodStart = request.periodStart,
				periodEnd = request.periodEnd,
				generatedBy = generatedBy
			)
		)
		rabbitTemplate.convertAndSend(
			RabbitMQConfig.EXCHANGE,
			RabbitMQConfig.ROUTING_KEY_REPORT_GENERATION,
			ReportGenerationMessage(requireNotNull(saved.id))
		)
		return saved
	}

	/**
	 * `GET /reports/{id}/download` — mints a fresh short-lived signed MinIO URL (same
	 * never-persist-the-URL-itself reasoning as [FileStorageService.presignedGetUrl] kdoc), or a
	 * 409 [ConflictException] if generation hasn't finished (or failed) yet. Deliberately does NOT
	 * 404 here (unlike e.g. IncidentService's ownership checks) — the report id itself is real and
	 * known, it's just not ready; a 409 with the current status tells the caller "come back later"
	 * instead of implying the id is wrong.
	 */
	fun getDownloadUrl(id: UUID): String {
		val report = findOrThrow(id)
		val fileUrl = report.fileUrl
		if (report.status != ReportStatus.READY || fileUrl == null) {
			throw ConflictException("error.report.not-ready", report.status)
		}
		return fileStorageService.presignedGetUrl(fileUrl)
	}
}

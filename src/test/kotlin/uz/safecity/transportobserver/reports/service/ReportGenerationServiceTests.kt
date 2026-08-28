package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.common.storage.FileStorageService
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.railsafe.entity.RailCrossingEvent
import uz.safecity.transportobserver.railsafe.entity.RailEventSeverity
import uz.safecity.transportobserver.railsafe.entity.RailEventType
import uz.safecity.transportobserver.railsafe.repository.RailCrossingEventRepository
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.entity.ReportStatus
import uz.safecity.transportobserver.reports.entity.ReportType
import uz.safecity.transportobserver.reports.repository.ReportRepository
import uz.safecity.transportobserver.shifts.entity.WorkShift
import uz.safecity.transportobserver.shifts.repository.WorkShiftRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

/**
 * Covers [ReportGenerationService.generate] directly — a plain, synchronous method call, NOT
 * routed through RabbitMQ (that wiring is [ReportGenerationListener], covered end-to-end by
 * [ReportServiceAsyncPipelineTests]). Calling it directly, on the same thread as the
 * `@Transactional` test method, is what makes `@Transactional` safe to use here: every DB
 * read/write this triggers joins the same test-managed transaction and rolls back cleanly,
 * unlike the real RabbitMQ consumer thread which has its own connection (see
 * [ReportServiceAsyncPipelineTests] kdoc for why THAT test is deliberately not transactional).
 *
 * Every period below is a wide `[now - 1h, now + 1h)` window rather than backdated fixture data
 * (contrast [uz.safecity.transportobserver.reports.service.ReportStatsServiceTests]'s day/month
 * precision tests) — this suite's job is confirming the generation PIPELINE wiring (each
 * [ReportType]'s query -> HTML -> PDF -> MinIO upload -> status transition), not exact period
 * boundary semantics.
 */
@SpringBootTest
@Transactional
class ReportGenerationServiceTests {

	@Autowired
	lateinit var reportGenerationService: ReportGenerationService

	@Autowired
	lateinit var reportRepository: ReportRepository

	@Autowired
	lateinit var incidentRepository: IncidentRepository

	@Autowired
	lateinit var inspectionRepository: InspectionRepository

	@Autowired
	lateinit var workShiftRepository: WorkShiftRepository

	@Autowired
	lateinit var railCrossingEventRepository: RailCrossingEventRepository

	@Autowired
	lateinit var checkpointService: CheckpointService

	@Autowired
	lateinit var fileStorageService: FileStorageService

	private val periodStart: Instant = Instant.now().minusSeconds(3600)
	private val periodEnd: Instant = Instant.now().plusSeconds(3600)

	private fun saveReport(type: ReportType, title: String = "Test ${UUID.randomUUID()}"): Report =
		reportRepository.save(
			Report(title = title, type = type, status = ReportStatus.PENDING, periodStart = periodStart, periodEnd = periodEnd)
		)

	@Test
	fun `generate renders and uploads a real PDF for INCIDENTS_SUMMARY`() {
		incidentRepository.save(Incident(title = "Test incident ${UUID.randomUUID()}", type = IncidentType.VIOLATION))

		val report = saveReport(ReportType.INCIDENTS_SUMMARY)
		reportGenerationService.generate(requireNotNull(report.id))

		val updated = reportRepository.findById(requireNotNull(report.id)).orElseThrow()
		assertEquals(ReportStatus.READY, updated.status)
		assertNotNull(updated.fileUrl)
		assertNull(updated.errorMessage)

		val pdfBytes = downloadViaPresignedUrl(requireNotNull(updated.fileUrl))
		assertTrue(pdfBytes.size > 4)
		assertEquals("%PDF", String(pdfBytes, 0, 4, Charsets.US_ASCII))
	}

	@Test
	fun `generate produces a READY report for EMPLOYEE_ACTIVITY`() {
		val checkpointId = checkpointService.create(
			CreateCheckpointRequest(name = "Test CP ${UUID.randomUUID()}", latitude = 41.3, longitude = 69.2)
		).id

		inspectionRepository.save(
			Inspection(checkpointId = checkpointId, status = InspectionStatus.COMPLETED, performedAt = Instant.now())
		)
		workShiftRepository.save(WorkShift(inspectorId = UUID.randomUUID(), startedAt = Instant.now()))

		val report = saveReport(ReportType.EMPLOYEE_ACTIVITY)
		reportGenerationService.generate(requireNotNull(report.id))

		val updated = reportRepository.findById(requireNotNull(report.id)).orElseThrow()
		assertEquals(ReportStatus.READY, updated.status)
		assertNotNull(updated.fileUrl)
	}

	@Test
	fun `generate produces a READY report for RAILSAFE_EVENTS`() {
		railCrossingEventRepository.save(
			RailCrossingEvent(
				crossingCode = "TEST-${UUID.randomUUID()}",
				eventType = RailEventType.VIOLATION,
				severity = RailEventSeverity.WARNING,
				detectedAt = Instant.now()
			)
		)

		val report = saveReport(ReportType.RAILSAFE_EVENTS)
		reportGenerationService.generate(requireNotNull(report.id))

		val updated = reportRepository.findById(requireNotNull(report.id)).orElseThrow()
		assertEquals(ReportStatus.READY, updated.status)
		assertNotNull(updated.fileUrl)
	}

	@Test
	fun `generate produces a READY report for CUSTOM with no data source wired up yet`() {
		val report = saveReport(ReportType.CUSTOM)
		reportGenerationService.generate(requireNotNull(report.id))

		val updated = reportRepository.findById(requireNotNull(report.id)).orElseThrow()
		assertEquals(ReportStatus.READY, updated.status)
		assertNotNull(updated.fileUrl)
	}

	@Test
	fun `generate silently skips a report id that no longer exists`() {
		// Must not throw — see ReportGenerationService#generate kdoc for why a vanished row is
		// logged and skipped rather than treated as an error.
		reportGenerationService.generate(UUID.randomUUID())
	}

	@Test
	fun `generate is idempotent when the same message is delivered twice`() {
		// Simulates RabbitMQ's at-least-once redelivery: the report is already READY (with a
		// fileUrl) from a first, successful `generate()` call. A second call for the same
		// reportId must be a no-op — it must not re-render/re-upload another PDF, and must not
		// clobber the terminal status back to GENERATING (see the idempotency-guard kdoc on
		// ReportGenerationService#generate).
		val report = saveReport(ReportType.CUSTOM)
		reportGenerationService.generate(requireNotNull(report.id))

		val afterFirstCall = reportRepository.findById(requireNotNull(report.id)).orElseThrow()
		assertEquals(ReportStatus.READY, afterFirstCall.status)
		val fileUrlAfterFirstCall = afterFirstCall.fileUrl
		assertNotNull(fileUrlAfterFirstCall)

		reportGenerationService.generate(requireNotNull(report.id))

		val afterSecondCall = reportRepository.findById(requireNotNull(report.id)).orElseThrow()
		assertEquals(ReportStatus.READY, afterSecondCall.status)
		assertEquals(fileUrlAfterFirstCall, afterSecondCall.fileUrl)
	}

	private fun downloadViaPresignedUrl(objectKey: String): ByteArray {
		val url = fileStorageService.presignedGetUrl(objectKey)
		val client = HttpClient.newHttpClient()
		val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
		val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
		assertEquals(200, response.statusCode())
		return response.body()
	}
}

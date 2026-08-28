package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.reports.dto.CreateReportRequest
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.entity.ReportStatus
import uz.safecity.transportobserver.reports.entity.ReportType
import uz.safecity.transportobserver.reports.repository.ReportRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID

/**
 * Covers the FULL real async pipeline end-to-end: `POST /reports` (ReportService#create) publishes
 * a real RabbitMQ message, this app's own [ReportGenerationListener] bean (already running as part
 * of this Spring context, exactly as in production) consumes it off a separate broker thread, and
 * [ReportGenerationService] renders + uploads a real PDF to the local MinIO.
 *
 * Deliberately NOT `@Transactional`, unlike every other test in this module: the listener consumes
 * on a separate thread with its own DB connection, so a report row inserted inside a still-open
 * (never-committed) `@Transactional` test transaction would be invisible to it — `findById` would
 * just come back empty and the listener would (correctly, per its own kdoc) skip generation
 * entirely, making this test either flaky or vacuous. Committing for real here is the only way to
 * exercise the actual code path a real `POST /reports` call takes in production; [tearDown] cleans
 * up the one row this test creates since there is no transaction rollback safety net.
 *
 * See [ReportServiceTests] for the synchronous, `@Transactional`-safe coverage of `create`/
 * `getDownloadUrl`, and [ReportGenerationServiceTests] for the generation logic itself invoked
 * directly (bypassing RabbitMQ) for each [ReportType].
 */
@SpringBootTest
class ReportServiceAsyncPipelineTests {

	@Autowired
	lateinit var reportService: ReportService

	@Autowired
	lateinit var reportRepository: ReportRepository

	private var createdReportId: UUID? = null

	@AfterEach
	fun tearDown() {
		createdReportId?.let { id -> reportRepository.findById(id).ifPresent { reportRepository.delete(it) } }
	}

	@Test
	fun `create publishes a message the real listener consumes end-to-end into READY`() {
		val request = CreateReportRequest(
			title = "E2E pipeline test ${UUID.randomUUID()}",
			type = ReportType.CUSTOM,
			periodStart = Instant.now().minusSeconds(3600),
			periodEnd = Instant.now()
		)

		val created = reportService.create(request, generatedBy = null)
		createdReportId = created.id
		assertEquals(ReportStatus.PENDING, created.status)

		val finalReport = awaitFinalStatus(requireNotNull(created.id))

		assertEquals(ReportStatus.READY, finalReport.status, "Report ended in ${finalReport.status} instead of READY: ${finalReport.errorMessage}")
		assertNotNull(finalReport.fileUrl)
	}

	/** Polls the DB (never the queue itself) until the listener has moved the report past PENDING/GENERATING, or [timeoutMs] elapses. */
	private fun awaitFinalStatus(id: UUID, timeoutMs: Long = 15_000): Report {
		val deadline = System.currentTimeMillis() + timeoutMs
		var current = reportRepository.findById(id).orElseThrow()
		while (current.status == ReportStatus.PENDING || current.status == ReportStatus.GENERATING) {
			if (System.currentTimeMillis() > deadline) break
			Thread.sleep(200)
			current = reportRepository.findById(id).orElseThrow()
		}
		return current
	}
}

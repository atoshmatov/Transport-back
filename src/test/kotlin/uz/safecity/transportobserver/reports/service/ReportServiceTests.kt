package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.reports.dto.CreateReportRequest
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.entity.ReportStatus
import uz.safecity.transportobserver.reports.entity.ReportType
import uz.safecity.transportobserver.reports.repository.ReportRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Covers `POST /api/v1/reports` (ReportService#create — saves PENDING) and
 * `GET /api/v1/reports/{id}/download`'s status-based error handling (ReportService#getDownloadUrl).
 *
 * Kept `@Transactional` (rolled back after each test) since neither method here depends on the
 * async RabbitMQ consumer actually finishing: [create]'s DB write + message publish are both
 * exercised (a publish is not tied to the JPA transaction, so it still goes out for real even
 * though the row itself is rolled back afterward — see [ReportServiceAsyncPipelineTests] for the
 * full real, non-transactional end-to-end pipeline test instead), and [getDownloadUrl] here is
 * tested purely against rows this test itself sets up with a fixed status, no listener involved.
 */
@SpringBootTest
@Transactional
class ReportServiceTests {

	@Autowired
	lateinit var reportService: ReportService

	@Autowired
	lateinit var reportRepository: ReportRepository

	@Test
	fun `create saves a new report with PENDING status`() {
		val request = CreateReportRequest(
			title = "Test hisobot ${UUID.randomUUID()}",
			type = ReportType.INCIDENTS_SUMMARY,
			periodStart = Instant.now().minusSeconds(3600),
			periodEnd = Instant.now()
		)

		val report = reportService.create(request, generatedBy = null)

		assertNotNull(report.id)
		assertEquals(ReportStatus.PENDING, report.status)
		assertEquals(request.title, report.title)
		assertEquals(ReportType.INCIDENTS_SUMMARY, report.type)

		val persisted = reportRepository.findById(requireNotNull(report.id)).orElseThrow()
		assertEquals(ReportStatus.PENDING, persisted.status)
	}

	@Test
	fun `getById throws for an unknown id`() {
		assertThrows(ResourceNotFoundException::class.java) {
			reportService.getById(UUID.randomUUID())
		}
	}

	@Test
	fun `getDownloadUrl rejects a PENDING report with a 409-mapped ConflictException`() {
		val pending = reportRepository.save(
			Report(title = "Pending report", type = ReportType.CUSTOM, status = ReportStatus.PENDING)
		)

		val ex = assertThrows(ConflictException::class.java) {
			reportService.getDownloadUrl(requireNotNull(pending.id))
		}
		assertTrue(ex.message!!.contains("PENDING"))
	}

	@Test
	fun `getDownloadUrl rejects a FAILED report`() {
		val failed = reportRepository.save(
			Report(title = "Failed report", type = ReportType.CUSTOM, status = ReportStatus.FAILED, errorMessage = "boom")
		)

		assertThrows(ConflictException::class.java) {
			reportService.getDownloadUrl(requireNotNull(failed.id))
		}
	}

	@Test
	fun `getDownloadUrl returns a signed URL once the report is READY`() {
		val ready = reportRepository.save(
			Report(
				title = "Ready report",
				type = ReportType.CUSTOM,
				status = ReportStatus.READY,
				fileUrl = "reports/${UUID.randomUUID()}/test.pdf"
			)
		)

		val url = reportService.getDownloadUrl(requireNotNull(ready.id))
		assertTrue(url.isNotBlank())
	}
}

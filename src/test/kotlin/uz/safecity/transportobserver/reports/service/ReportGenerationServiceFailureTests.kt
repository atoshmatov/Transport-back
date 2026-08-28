package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.common.storage.FileStorageService
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.entity.ReportStatus
import uz.safecity.transportobserver.reports.entity.ReportType
import uz.safecity.transportobserver.reports.repository.ReportRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional

/**
 * Covers [ReportGenerationService.generate]'s FAILED path: [FileStorageService] is mocked to throw
 * on upload (simulating MinIO being unreachable) so the exception must be caught and turned into
 * `status = FAILED` + [Report.errorMessage], never left stuck on GENERATING and never rethrown —
 * a rethrow would hit the real RabbitMQ listener in production and trigger endless redelivery.
 *
 * Split into its own test class (rather than added to [ReportGenerationServiceTests]) because
 * `@MockBean` replaces [FileStorageService] for every bean in this class's whole Spring context —
 * Spring Test caches that context separately from the unmocked one the rest of this module's tests
 * share, so isolating it here avoids paying that extra context-reload cost on every other test.
 */
@SpringBootTest
@Transactional
class ReportGenerationServiceFailureTests {

	@Autowired
	lateinit var reportGenerationService: ReportGenerationService

	@Autowired
	lateinit var reportRepository: ReportRepository

	@MockBean
	lateinit var fileStorageService: FileStorageService

	/**
	 * `Mockito.any()`/`any(Class)` returns raw Java `null`, but [FileStorageService.upload]'s
	 * `bytes` parameter is a non-null Kotlin `ByteArray` — the Kotlin compiler inserts a
	 * not-null check on the RESULT of a call used directly as a non-null-typed argument, so
	 * `any(ByteArray::class.java)` spliced straight into the call below throws
	 * `NullPointerException: any(...) must not be null` before Mockito ever gets to record the
	 * stub. Calling `Mockito.any(...)` here for its matcher-registration side effect only, then
	 * separately returning a real (empty) [ByteArray] literal, keeps Mockito's matcher stack
	 * correct without ever handing a null value to a non-null-typed argument slot.
	 */
	private fun anyByteArray(): ByteArray {
		Mockito.any(ByteArray::class.java)
		return ByteArray(0)
	}

	@Test
	fun `generate flips a report to FAILED when the MinIO upload throws`() {
		doThrow(RuntimeException("MinIO unreachable (test)"))
			.`when`(fileStorageService).upload(anyString(), anyByteArray(), anyString())

		val report = reportRepository.save(
			Report(title = "Will fail", type = ReportType.CUSTOM, status = ReportStatus.PENDING)
		)

		reportGenerationService.generate(requireNotNull(report.id))

		val updated = reportRepository.findById(requireNotNull(report.id)).orElseThrow()
		assertEquals(ReportStatus.FAILED, updated.status)
		assertNotNull(updated.errorMessage)
		assertEquals("MinIO unreachable (test)", updated.errorMessage)
	}
}

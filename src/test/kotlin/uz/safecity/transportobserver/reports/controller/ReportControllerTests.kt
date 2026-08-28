package uz.safecity.transportobserver.reports.controller

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.entity.ReportStatus
import uz.safecity.transportobserver.reports.entity.ReportType
import uz.safecity.transportobserver.reports.repository.ReportRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the `@PreAuthorize` role gate on [ReportController.list]/[ReportController.getById] — a
 * HIGH-severity gap fixed here: previously any authenticated account (ROLE_INSPECTOR included)
 * could list/read every generated report, contradicting the controller's SUPER_ADMIN/ADMIN/OPERATOR
 * intent. Also covers that both endpoints now return [uz.safecity.transportobserver.reports.dto.ReportDto]
 * rather than the raw [Report] entity, so `fileUrl`/`errorMessage` never appear on the wire — see
 * [uz.safecity.transportobserver.reports.dto.ReportDto] kdoc. Uses the same `authentication(...)`
 * `RequestPostProcessor` approach as [ReportStatsControllerTests].
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var reportRepository: ReportRepository

	private fun createAccount(role: RoleType): Account = accountRepository.save(
		Account(
			username = "${role.name.lowercase()}_${UUID.randomUUID().toString().take(20)}",
			passwordHash = "irrelevant-for-this-test",
			role = role,
			mustChangePassword = false,
			isActive = true
		)
	)

	private fun authOf(account: Account): RequestPostProcessor {
		val principal = CustomUserDetails.from(account)
		return authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities))
	}

	private fun createReport(status: ReportStatus = ReportStatus.READY): Report = reportRepository.save(
		Report(
			title = "Test report ${UUID.randomUUID()}",
			type = ReportType.CUSTOM,
			status = status,
			fileUrl = if (status == ReportStatus.READY) "reports/${UUID.randomUUID()}/test.pdf" else null,
			errorMessage = if (status == ReportStatus.FAILED) "internal MinIO connection failure detail" else null
		)
	)

	@Test
	fun `an INSPECTOR is rejected from list with 403`() {
		val inspector = createAccount(RoleType.INSPECTOR)

		mockMvc.perform(get("/api/v1/reports").with(authOf(inspector)))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `an INSPECTOR is rejected from getById with 403`() {
		val inspector = createAccount(RoleType.INSPECTOR)
		val report = createReport()

		mockMvc.perform(get("/api/v1/reports/${report.id}").with(authOf(inspector)))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `an ADMIN can list reports and the response omits fileUrl and errorMessage`() {
		val admin = createAccount(RoleType.ADMIN)
		val report = createReport(status = ReportStatus.FAILED)

		mockMvc.perform(get("/api/v1/reports").with(authOf(admin)))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data[0].fileUrl").doesNotExist())
			.andExpect(jsonPath("$.data[0].errorMessage").doesNotExist())
	}

	@Test
	fun `an ADMIN can read a report by id as a DTO without fileUrl or errorMessage`() {
		val admin = createAccount(RoleType.ADMIN)
		val report = createReport(status = ReportStatus.FAILED)

		mockMvc.perform(get("/api/v1/reports/${report.id}").with(authOf(admin)))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.id").value(report.id.toString()))
			.andExpect(jsonPath("$.data.status").value("FAILED"))
			.andExpect(jsonPath("$.data.fileUrl").doesNotExist())
			.andExpect(jsonPath("$.data.errorMessage").doesNotExist())
	}
}

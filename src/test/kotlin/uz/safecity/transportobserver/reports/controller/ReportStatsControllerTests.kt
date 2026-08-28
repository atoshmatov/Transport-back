package uz.safecity.transportobserver.reports.controller

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the `@PreAuthorize` role gate on [ReportStatsController] — specifically that
 * `GET /activity`, `GET /regions-distribution` and `GET /checkpoints-distribution` now also allow
 * ROLE_INSPECTOR (the mobile TransportO app's Stats screen), while `GET /dashboard` stays
 * SUPER_ADMIN/ADMIN/OPERATOR-only — see the controller's class kdoc for why. Uses the same
 * `authentication(...)` `RequestPostProcessor` approach as
 * [uz.safecity.transportobserver.employees.controller.AdminEmployeePositionHistoryControllerTests]
 * rather than `@WithMockUser`, since none of these endpoints bind `@AuthenticationPrincipal`, but a
 * real [CustomUserDetails] keeps this test consistent with the rest of the suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportStatsControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var accountRepository: AccountRepository

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

	@Test
	fun `an INSPECTOR can call activity, regions-distribution and checkpoints-distribution`() {
		val inspector = createAccount(RoleType.INSPECTOR)

		mockMvc.perform(get("/api/v1/reports/activity").with(authOf(inspector)))
			.andExpect(status().isOk)
		mockMvc.perform(get("/api/v1/reports/regions-distribution").with(authOf(inspector)))
			.andExpect(status().isOk)
		mockMvc.perform(get("/api/v1/reports/checkpoints-distribution").with(authOf(inspector)))
			.andExpect(status().isOk)
	}

	@Test
	fun `an INSPECTOR is still rejected from dashboard with 403`() {
		val inspector = createAccount(RoleType.INSPECTOR)

		mockMvc.perform(get("/api/v1/reports/dashboard").with(authOf(inspector)))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `an ADMIN can still call every endpoint, dashboard included`() {
		val admin = createAccount(RoleType.ADMIN)

		mockMvc.perform(get("/api/v1/reports/dashboard").with(authOf(admin)))
			.andExpect(status().isOk)
		mockMvc.perform(get("/api/v1/reports/activity").with(authOf(admin)))
			.andExpect(status().isOk)
		mockMvc.perform(get("/api/v1/reports/regions-distribution").with(authOf(admin)))
			.andExpect(status().isOk)
		mockMvc.perform(get("/api/v1/reports/checkpoints-distribution").with(authOf(admin)))
			.andExpect(status().isOk)
	}
}

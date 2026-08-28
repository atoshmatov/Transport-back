package uz.safecity.transportobserver.employees.controller

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.employees.dto.CreateEmployeeRequest
import uz.safecity.transportobserver.employees.dto.UpdateEmployeeRequest
import uz.safecity.transportobserver.employees.service.EmployeeService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers `GET /api/v1/admin/employees/{id}/position-history` — see
 * [uz.safecity.transportobserver.employees.entity.EmployeePositionHistory] kdoc for the underlying
 * jurnal. Uses the same `authentication(...)` `RequestPostProcessor` approach as
 * [uz.safecity.transportobserver.incidents.controller.IncidentEvidenceControllerTests] rather than
 * `@WithMockUser` — this controller doesn't bind `@AuthenticationPrincipal` on this endpoint, but
 * the same real-[CustomUserDetails] pattern keeps this test consistent with the rest of the suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminEmployeePositionHistoryControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var employeeService: EmployeeService

	private fun createAdmin(): Account = accountRepository.save(
		Account(
			username = "admin_${UUID.randomUUID().toString().take(20)}",
			passwordHash = "irrelevant-for-this-test",
			role = RoleType.SUPER_ADMIN,
			mustChangePassword = false,
			isActive = true
		)
	)

	private fun authOf(account: Account): RequestPostProcessor {
		val principal = CustomUserDetails.from(account)
		return authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities))
	}

	@Test
	fun `returns the employee's spells newest-first after a position change`() {
		val admin = createAdmin()
		val employee = employeeService.create(
			CreateEmployeeRequest(fullName = "Test Employee", position = "Inspektor", role = RoleType.INSPECTOR),
			actorAccountId = admin.id,
			actorRole = RoleType.SUPER_ADMIN
		).employee

		employeeService.update(
			employee.id,
			UpdateEmployeeRequest(fullName = employee.fullName, position = "Katta inspektor"),
			actorAccountId = admin.id,
			actorRole = RoleType.SUPER_ADMIN
		)

		mockMvc.perform(get("/api/v1/admin/employees/{id}/position-history", employee.id).with(authOf(admin)))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].position").value("Katta inspektor"))
			.andExpect(jsonPath("$.data[0].endedAt").doesNotExist())
			.andExpect(jsonPath("$.data[1].position").value("Inspektor"))
			.andExpect(jsonPath("$.data[1].endedAt").exists())
	}

	@Test
	fun `an unknown employee id returns 404`() {
		val admin = createAdmin()

		mockMvc.perform(get("/api/v1/admin/employees/{id}/position-history", UUID.randomUUID()).with(authOf(admin)))
			.andExpect(status().isNotFound)
	}

	@Test
	fun `a non-admin caller is rejected with 403`() {
		val inspector = accountRepository.save(
			Account(
				username = "insp_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.INSPECTOR,
				mustChangePassword = false,
				isActive = true
			)
		)

		mockMvc.perform(get("/api/v1/admin/employees/{id}/position-history", UUID.randomUUID()).with(authOf(inspector)))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `an old-style update request without the new HR fields still succeeds`() {
		val admin = createAdmin()
		val employee = employeeService.create(
			CreateEmployeeRequest(fullName = "Legacy Client Employee", position = "Inspektor", role = RoleType.INSPECTOR),
			actorAccountId = admin.id,
			actorRole = RoleType.SUPER_ADMIN
		).employee

		// Simulates a pre-existing client sending only the original fields, no HR JSON keys at all.
		val legacyBody = objectMapper.writeValueAsString(
			mapOf(
				"fullName" to "Legacy Client Employee Updated",
				"position" to "Inspektor",
				"department" to null,
				"regionName" to null,
				"phoneNumber" to null,
				"hiredAt" to null
			)
		)

		mockMvc.perform(
			put("/api/v1/admin/employees/{id}", employee.id)
				.with(authOf(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content(legacyBody)
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.fullName").value("Legacy Client Employee Updated"))
			.andExpect(jsonPath("$.data.personalId").doesNotExist())
	}
}

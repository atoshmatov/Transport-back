package uz.safecity.transportobserver.employees.service

import uz.safecity.transportobserver.audit.repository.AuditLogRepository
import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Regression coverage for [EmployeeService.resetPassword] after it was updated to pass
 * `actorRole` through to [uz.safecity.transportobserver.auth.service.AuthService.resetPassword]
 * (which now applies its own `RoleHierarchyGuard` check too — see that method's kdoc for why the
 * check is deliberately duplicated across both services).
 */
@SpringBootTest
@Transactional
class EmployeeServiceResetPasswordTests {

	@Autowired
	lateinit var employeeService: EmployeeService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var employeeRepository: EmployeeRepository

	@Autowired
	lateinit var auditLogRepository: AuditLogRepository

	private fun createEmployeeWithAccount(role: RoleType): Pair<Employee, Account> {
		val employee = employeeRepository.save(Employee(fullName = "Test ${UUID.randomUUID().toString().take(8)}"))
		val account = accountRepository.save(
			Account(
				username = "u_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = role,
				employeeId = employee.id,
				mustChangePassword = false,
				isActive = true
			)
		)
		return employee to account
	}

	@Test
	fun `ADMIN resetting an OPERATOR employee's password succeeds and writes both audit entries`() {
		val (_, adminAccount) = createEmployeeWithAccount(RoleType.ADMIN)
		val (operatorEmployee, operatorAccount) = createEmployeeWithAccount(RoleType.OPERATOR)

		val response = employeeService.resetPassword(
			requireNotNull(operatorEmployee.id),
			adminAccount.id,
			RoleType.ADMIN
		)

		assertEquals(operatorAccount.id, response.accountId)

		val allEntries = auditLogRepository.findAll()
		assertTrue(allEntries.any { it.action == "EMPLOYEE_PASSWORD_RESET" && it.entityId == operatorEmployee.id })
		assertTrue(allEntries.any { it.action == "ACCOUNT_PASSWORD_RESET" && it.entityId == operatorAccount.id })
	}

	@Test
	fun `ADMIN cannot reset another ADMIN employee's password via the Employee-scoped endpoint`() {
		val (_, actingAdminAccount) = createEmployeeWithAccount(RoleType.ADMIN)
		val (targetAdminEmployee, targetAdminAccount) = createEmployeeWithAccount(RoleType.ADMIN)

		val ex = assertThrows(ForbiddenException::class.java) {
			employeeService.resetPassword(
				requireNotNull(targetAdminEmployee.id),
				actingAdminAccount.id,
				RoleType.ADMIN
			)
		}
		assertEquals("error.employee.role-create-forbidden", ex.messageKey)

		val entries = auditLogRepository.findAll()
			.filter { it.entityId == targetAdminEmployee.id || it.entityId == targetAdminAccount.id }
		assertTrue(entries.isEmpty())
	}
}

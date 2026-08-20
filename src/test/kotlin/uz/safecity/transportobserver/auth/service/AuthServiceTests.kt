package uz.safecity.transportobserver.auth.service

import uz.safecity.transportobserver.audit.repository.AuditLogRepository
import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.RefreshTokenService
import uz.safecity.transportobserver.common.exception.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the privilege-escalation gap fixed alongside this test class: `AuthService.resetPassword`
 * (reachable directly via `POST /api/v1/auth/reset-password`, not just through
 * [uz.safecity.transportobserver.employees.service.EmployeeService.resetPassword]'s Employee-scoped
 * endpoint) previously applied NO actor-vs-target role-hierarchy check, so an ADMIN could reset the
 * password of another ADMIN or SUPER_ADMIN by calling this endpoint directly with an arbitrary
 * accountId. It now delegates to the shared `RoleHierarchyGuard` (also used by `EmployeeService`) and
 * always records its own "ACCOUNT_PASSWORD_RESET" audit entry.
 */
@SpringBootTest
@Transactional
class AuthServiceTests {

	@Autowired
	lateinit var authService: AuthService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var auditLogRepository: AuditLogRepository

	@Autowired
	lateinit var passwordEncoder: PasswordEncoder

	@Autowired
	lateinit var refreshTokenService: RefreshTokenService

	private fun createAccount(role: RoleType): Account {
		val account = Account(
			username = "acct_${UUID.randomUUID().toString().take(20)}",
			passwordHash = passwordEncoder.encode("Original123!"),
			role = role,
			mustChangePassword = false,
			isActive = true
		)
		return accountRepository.save(account)
	}

	@Test
	fun `SUPER_ADMIN can reset the password of any role, including another SUPER_ADMIN`() {
		val superAdmin = createAccount(RoleType.SUPER_ADMIN)
		val target = createAccount(RoleType.SUPER_ADMIN)
		val originalHash = target.passwordHash

		val response = authService.resetPassword(requireNotNull(target.id), superAdmin.id, RoleType.SUPER_ADMIN)

		assertEquals(target.id, response.accountId)
		assertEquals(target.username, response.username)
		assertTrue(response.temporaryPassword.isNotBlank())

		val reloaded = accountRepository.findById(requireNotNull(target.id)).orElseThrow()
		assertNotEquals(originalHash, reloaded.passwordHash)
		assertTrue(reloaded.mustChangePassword)
	}

	@Test
	fun `ADMIN can reset the password of an OPERATOR`() {
		val admin = createAccount(RoleType.ADMIN)
		val operator = createAccount(RoleType.OPERATOR)

		val response = authService.resetPassword(requireNotNull(operator.id), admin.id, RoleType.ADMIN)

		assertEquals(operator.id, response.accountId)
		val reloaded = accountRepository.findById(requireNotNull(operator.id)).orElseThrow()
		assertTrue(reloaded.mustChangePassword)
	}

	@Test
	fun `ADMIN can reset the password of an INSPECTOR`() {
		val admin = createAccount(RoleType.ADMIN)
		val inspector = createAccount(RoleType.INSPECTOR)

		val response = authService.resetPassword(requireNotNull(inspector.id), admin.id, RoleType.ADMIN)

		assertEquals(inspector.id, response.accountId)
	}

	@Test
	fun `ADMIN cannot reset the password of another ADMIN`() {
		val admin = createAccount(RoleType.ADMIN)
		val otherAdmin = createAccount(RoleType.ADMIN)
		val originalHash = otherAdmin.passwordHash

		val ex = assertThrows(ForbiddenException::class.java) {
			authService.resetPassword(requireNotNull(otherAdmin.id), admin.id, RoleType.ADMIN)
		}
		assertEquals("error.employee.role-create-forbidden", ex.messageKey)

		// Nothing must have been mutated by the rejected call.
		val reloaded = accountRepository.findById(requireNotNull(otherAdmin.id)).orElseThrow()
		assertEquals(originalHash, reloaded.passwordHash)
		assertFalse(reloaded.mustChangePassword)
	}

	@Test
	fun `ADMIN cannot reset the password of a SUPER_ADMIN`() {
		val admin = createAccount(RoleType.ADMIN)
		val superAdmin = createAccount(RoleType.SUPER_ADMIN)
		val originalHash = superAdmin.passwordHash

		val ex = assertThrows(ForbiddenException::class.java) {
			authService.resetPassword(requireNotNull(superAdmin.id), admin.id, RoleType.ADMIN)
		}
		assertEquals("error.employee.role-create-forbidden", ex.messageKey)

		val reloaded = accountRepository.findById(requireNotNull(superAdmin.id)).orElseThrow()
		assertEquals(originalHash, reloaded.passwordHash)
	}

	@Test
	fun `resetPassword records an ACCOUNT_PASSWORD_RESET audit entry without leaking the temporary password`() {
		val superAdmin = createAccount(RoleType.SUPER_ADMIN)
		val target = createAccount(RoleType.OPERATOR)

		val response = authService.resetPassword(requireNotNull(target.id), superAdmin.id, RoleType.SUPER_ADMIN)

		val entries = auditLogRepository.findAll()
			.filter { it.action == "ACCOUNT_PASSWORD_RESET" && it.entityId == target.id }
		assertEquals(1, entries.size)
		val entry = entries.single()
		assertEquals(superAdmin.id, entry.actorAccountId)
		assertEquals("Account", entry.entityType)
		assertEquals(target.id, entry.entityId)

		// The one-time temporary password must never end up in the audit trail.
		assertFalse(entry.metadata?.contains(response.temporaryPassword) ?: false)
		assertNotNull(response.temporaryPassword)
	}

	@Test
	fun `resetPassword revokes every existing refresh token session for the target account`() {
		val superAdmin = createAccount(RoleType.SUPER_ADMIN)
		val target = createAccount(RoleType.OPERATOR)
		val liveToken = refreshTokenService.issue(requireNotNull(target.id))
		assertEquals(target.id, refreshTokenService.resolveAccountId(liveToken))

		authService.resetPassword(requireNotNull(target.id), superAdmin.id, RoleType.SUPER_ADMIN)

		assertEquals(null, refreshTokenService.resolveAccountId(liveToken))
	}

	@Test
	fun `a rejected resetPassword call does not record an audit entry`() {
		val admin = createAccount(RoleType.ADMIN)
		val otherAdmin = createAccount(RoleType.ADMIN)

		assertThrows(ForbiddenException::class.java) {
			authService.resetPassword(requireNotNull(otherAdmin.id), admin.id, RoleType.ADMIN)
		}

		val entries = auditLogRepository.findAll()
			.filter { it.action == "ACCOUNT_PASSWORD_RESET" && it.entityId == otherAdmin.id }
		assertTrue(entries.isEmpty())
	}
}

package uz.safecity.transportobserver.auth.service

import uz.safecity.transportobserver.auth.dto.AccountSummary
import uz.safecity.transportobserver.auth.dto.ChangePasswordRequest
import uz.safecity.transportobserver.auth.dto.LoginRequest
import uz.safecity.transportobserver.auth.dto.LoginResponse
import uz.safecity.transportobserver.auth.dto.RefreshResponse
import uz.safecity.transportobserver.auth.dto.ResetPasswordResponse
import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.RefreshTokenService
import uz.safecity.transportobserver.auth.security.JwtService
import uz.safecity.transportobserver.auth.security.TemporaryPasswordGenerator
import uz.safecity.transportobserver.common.exception.AccountDisabledException
import uz.safecity.transportobserver.common.exception.AccountLockedException
import uz.safecity.transportobserver.common.exception.InvalidCredentialsException
import uz.safecity.transportobserver.common.exception.RefreshTokenInvalidException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
	private val accountRepository: AccountRepository,
	private val passwordEncoder: PasswordEncoder,
	private val jwtService: JwtService,
	private val refreshTokenService: RefreshTokenService,
	private val temporaryPasswordGenerator: TemporaryPasswordGenerator,
	private val employeeRepository: EmployeeRepository,
	@Value("\${security.lockout.max-attempts}") private val maxAttempts: Int,
	@Value("\${security.lockout.lock-duration-minutes}") private val lockDurationMinutes: Long
) {

	/**
	 * Returns the response DTO paired with the raw refresh token so the controller can put the
	 * token in an HttpOnly cookie — it must NEVER end up in [LoginResponse] itself, see that
	 * class's kdoc.
	 */
	@Transactional
	fun login(request: LoginRequest): Pair<LoginResponse, String> {
		val account = accountRepository.findByUsername(request.username)
			.orElseThrow { InvalidCredentialsException() }

		if (!account.isActive) throw AccountDisabledException()
		if (account.isCurrentlyLocked()) throw AccountLockedException()

		val accountId = requireNotNull(account.id)

		if (!passwordEncoder.matches(request.password, account.passwordHash)) {
			// Atomic UPDATE-based increment — see AccountRepository.incrementFailedAttempts
			// kdoc for why this avoids the find-then-save "lost update" race.
			accountRepository.incrementFailedAttempts(accountId)
			accountRepository.lockIfThresholdReached(
				accountId,
				maxAttempts,
				Instant.now().plus(Duration.ofMinutes(lockDurationMinutes))
			)
			val refreshed = accountRepository.findById(accountId).orElseThrow { InvalidCredentialsException() }
			if (refreshed.isCurrentlyLocked()) throw AccountLockedException()
			throw InvalidCredentialsException()
		}

		accountRepository.resetFailedAttempts(accountId)

		return buildLoginResponse(account)
	}

	/** Same body/cookie split as [login] — see its kdoc. */
	@Transactional
	fun refresh(refreshToken: String): Pair<RefreshResponse, String> {
		val accountId = refreshTokenService.resolveAccountId(refreshToken)
			?: throw RefreshTokenInvalidException()

		val account = accountRepository.findById(accountId)
			.orElseThrow { RefreshTokenInvalidException() }

		if (!account.isActive || account.isCurrentlyLocked()) {
			refreshTokenService.revoke(refreshToken)
			throw RefreshTokenInvalidException()
		}

		val newRefreshToken = refreshTokenService.rotate(refreshToken, accountId)
		val accessToken = jwtService.generateAccessToken(
			accountId = requireNotNull(account.id),
			username = account.username,
			role = account.role.name
		)

		val response = RefreshResponse(
			accessToken = accessToken,
			expiresInSeconds = jwtService.accessTokenTtlSeconds()
		)
		return response to newRefreshToken
	}

	/** [refreshToken] is null when the cookie was already missing — nothing to revoke, still a no-op success. */
	fun logout(refreshToken: String?) {
		refreshToken?.let { refreshTokenService.revoke(it) }
	}

	@Transactional
	fun changePassword(accountId: UUID, request: ChangePasswordRequest) {
		val account = accountRepository.findById(accountId)
			.orElseThrow { ResourceNotFoundException("Account topilmadi") }

		if (!passwordEncoder.matches(request.currentPassword, account.passwordHash)) {
			throw InvalidCredentialsException("Eski parol noto'g'ri")
		}

		account.passwordHash = passwordEncoder.encode(request.newPassword)
		account.mustChangePassword = false
		accountRepository.save(account)

		// Force re-login on all other devices/sessions after a password change.
		refreshTokenService.revokeAllForAccount(accountId)
	}

	/** ADMIN/SUPER_ADMIN only — see SecurityConfig authorization rule for /auth/reset-password. */
	@Transactional
	fun resetPassword(accountId: UUID): ResetPasswordResponse {
		val account = accountRepository.findById(accountId)
			.orElseThrow { ResourceNotFoundException("Account topilmadi") }

		val temporaryPassword = temporaryPasswordGenerator.generate()
		account.passwordHash = passwordEncoder.encode(temporaryPassword)
		account.mustChangePassword = true
		accountRepository.save(account)

		refreshTokenService.revokeAllForAccount(accountId)

		return ResetPasswordResponse(
			accountId = requireNotNull(account.id),
			username = account.username,
			temporaryPassword = temporaryPassword
		)
	}

	/** Revoke every session immediately — called by EmployeeService when an admin blocks/deletes an account. */
	fun revokeAllSessions(accountId: UUID) {
		refreshTokenService.revokeAllForAccount(accountId)
	}

	private fun buildLoginResponse(account: Account): Pair<LoginResponse, String> {
		val accountId = requireNotNull(account.id)
		val accessToken = jwtService.generateAccessToken(accountId, account.username, account.role.name)
		val refreshToken = refreshTokenService.issue(accountId)

		val response = LoginResponse(
			accessToken = accessToken,
			expiresInSeconds = jwtService.accessTokenTtlSeconds(),
			mustChangePassword = account.mustChangePassword,
			account = toAccountSummary(account)
		)
		return response to refreshToken
	}

	/**
	 * Resolves [AccountSummary.fullName] through [uz.safecity.transportobserver.employees.entity.Employee.fullName]
	 * via [Account.employeeId] — the same plain-FK-column link every other module reads (Account.employeeId is
	 * not a mapped JPA relation, see Account kdoc), so this is a direct lookup rather than a join.
	 *
	 * Falls back to [Account.username] — never left null — when there's no linked Employee (e.g. the
	 * SUPER_ADMIN bootstrap account, see Account kdoc) or the linked id doesn't resolve (should not
	 * happen in practice; defensive only). The frontend's `AuthUser.fullName` (src/shared/types/auth.ts)
	 * is displayed directly in the header/profile UI, so this avoids ever rendering a blank name there.
	 */
	private fun toAccountSummary(account: Account): AccountSummary {
		val employeeFullName = account.employeeId?.let { employeeRepository.findById(it).orElse(null)?.fullName }
		return AccountSummary(
			id = requireNotNull(account.id),
			username = account.username,
			role = account.role,
			employeeId = account.employeeId,
			fullName = employeeFullName ?: account.username
		)
	}
}

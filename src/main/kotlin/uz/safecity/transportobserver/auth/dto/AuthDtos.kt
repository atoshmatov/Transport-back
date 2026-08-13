package uz.safecity.transportobserver.auth.dto

import uz.safecity.transportobserver.auth.entity.RoleType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class LoginRequest(
	@field:NotBlank(message = "username majburiy")
	val username: String,

	@field:NotBlank(message = "password majburiy")
	val password: String
)

/**
 * The refresh token is deliberately NOT included here anymore — it travels as an
 * HttpOnly cookie (see [uz.safecity.transportobserver.auth.security.RefreshCookieFactory])
 * so client-side JS never has access to it. See AuthController kdoc for the full
 * cookie contract (name/path/attributes).
 */
data class LoginResponse(
	val accessToken: String,
	val tokenType: String = "Bearer",
	val expiresInSeconds: Long,
	val mustChangePassword: Boolean,
	val account: AccountSummary
)

/** Same cookie-not-body rule as [LoginResponse] — see its kdoc. */
data class RefreshResponse(
	val accessToken: String,
	val tokenType: String = "Bearer",
	val expiresInSeconds: Long
)

data class ChangePasswordRequest(
	@field:NotBlank(message = "eski parol majburiy")
	val currentPassword: String,

	@field:NotBlank(message = "yangi parol majburiy")
	// max=72: BCrypt silently truncates/ignores bytes past 72 — anything longer
	// wouldn't actually add entropy, so reject it explicitly instead of
	// accepting a password whose "extra" characters never do anything.
	@field:Size(min = 8, max = 72, message = "yangi parol 8 dan 72 belgigacha bo'lishi kerak")
	val newPassword: String
)

data class ResetPasswordRequest(
	@field:NotBlank
	val accountId: UUID
)

data class ResetPasswordResponse(
	val accountId: UUID,
	val username: String,
	/** Temporary password shown once to the admin so it can be handed to the employee out-of-band. */
	val temporaryPassword: String
)

data class CreateAccountRequest(
	@field:NotBlank
	val username: String,

	val role: RoleType,

	val employeeId: UUID? = null
)

data class AccountSummary(
	val id: UUID,
	val username: String,
	val role: RoleType,
	val employeeId: UUID?,

	/**
	 * [uz.safecity.transportobserver.employees.entity.Employee.fullName] for [employeeId] — see
	 * [uz.safecity.transportobserver.auth.service.AuthService.toAccountSummary] for exactly how
	 * this is resolved and why it falls back to [username] rather than staying null when there's
	 * no linked Employee row (e.g. a SUPER_ADMIN bootstrap account — see Account kdoc).
	 */
	val fullName: String?
)

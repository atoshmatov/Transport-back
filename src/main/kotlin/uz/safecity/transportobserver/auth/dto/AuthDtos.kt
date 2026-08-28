package uz.safecity.transportobserver.auth.dto

import uz.safecity.transportobserver.auth.entity.RoleType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LoginRequest(
	@field:NotBlank(message = "username majburiy")
	val username: String,

	@field:NotBlank(message = "password majburiy")
	val password: String
)

/**
 * The refresh token is NOT included here for web callers — it travels as an HttpOnly cookie (see
 * [uz.safecity.transportobserver.auth.security.RefreshCookieFactory]) so client-side JS never has
 * access to it. See AuthController kdoc for the full cookie contract (name/path/attributes).
 *
 * [refreshToken] is the one exception: it is populated ONLY when the request carried
 * [uz.safecity.transportobserver.auth.security.ClientPlatform.HEADER_NAME] identifying a native
 * mobile caller (see [uz.safecity.transportobserver.auth.controller.AuthController.login]) —
 * Kotlin/Native's Ktor `HttpClient` has no cookie jar, so the mobile app cannot read a `Set-Cookie`
 * response header and needs the token in the body instead. Web requests never send that header, so
 * this field stays `null` for them and — thanks to `spring.jackson.default-property-inclusion:
 * non_null` (application.yml) — is omitted from the JSON entirely, leaving the web wire contract
 * byte-for-byte unchanged.
 */
data class LoginResponse(
	val accessToken: String,
	val tokenType: String = "Bearer",
	val expiresInSeconds: Long,
	val mustChangePassword: Boolean,
	val account: AccountSummary,
	val refreshToken: String? = null
)

/** Same cookie-vs-mobile-body rule as [LoginResponse] — see its kdoc. */
data class RefreshResponse(
	val accessToken: String,
	val tokenType: String = "Bearer",
	val expiresInSeconds: Long,
	val refreshToken: String? = null
)

/**
 * Optional body for `POST /api/v1/auth/refresh`. Web callers send no body at all (refresh token
 * comes from the cookie); the mobile app — which has no cookie jar, see [LoginResponse] kdoc —
 * sends its stored refresh token here instead. See [uz.safecity.transportobserver.auth.controller.AuthController.refresh]
 * for how the two sources are reconciled.
 */
data class RefreshRequest(
	val refreshToken: String? = null
)

/** Same optional-body rationale as [RefreshRequest], for `POST /api/v1/auth/logout`. */
data class LogoutRequest(
	val refreshToken: String? = null
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

/**
 * One row in the "Faol sessiyalar" (Active Sessions) list — Profile > Faoliyat tab in the web
 * admin panel.
 *
 * [id] is a one-way hash of the underlying refresh token (see
 * [uz.safecity.transportobserver.auth.security.RefreshTokenService.hashSessionId] kdoc) — the raw
 * token itself is the bearer credential for `/auth/refresh` and must never appear in a response
 * body, so this opaque id is what `DELETE /api/v1/auth/sessions/{id}` takes instead.
 *
 * [current] marks whether THIS session is the one the calling request itself authenticated with,
 * so the frontend can hide/disable the "Chiqarish" (sign-out) action for it. This is computed
 * server-side from the actual cookie/header-supplied token on the request
 * ([uz.safecity.transportobserver.auth.controller.AuthController.getSessions]) — never guessed or
 * defaulted — because getting it wrong would let a user accidentally revoke the very session
 * they're using right now.
 */
data class SessionDto(
	val id: String,
	/** One of "WEB", "ANDROID", "IOS", "MOBILE" — see [uz.safecity.transportobserver.auth.security.SessionDevice.resolvePlatform]. */
	val platform: String,
	/** Short "Browser · OS" style label, or null when the User-Agent was missing/unparseable. */
	val device: String?,
	val createdAt: Instant,
	val lastUsedAt: Instant,
	val current: Boolean
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

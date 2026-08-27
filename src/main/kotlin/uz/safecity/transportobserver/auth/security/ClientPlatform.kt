package uz.safecity.transportobserver.auth.security

/**
 * Identifies API requests coming from the native mobile app (Kotlin/Native Ktor `HttpClient`, see
 * `TransportO/core/network/HttpClientFactory.kt`) so [uz.safecity.transportobserver.auth.controller.AuthController]
 * can hand the refresh token back in the JSON response body for it, in addition to the HttpOnly
 * cookie every caller gets — see [uz.safecity.transportobserver.auth.dto.LoginResponse] kdoc for the
 * full rationale (the mobile Ktor client has no cookie jar installed, so it cannot read `Set-Cookie`).
 *
 * Trust note: this is a self-reported client hint, not an authentication signal — trivially spoofable
 * by anyone. That is safe here because setting it only changes whether the SAME refresh token the
 * caller is already legitimately receiving via `Set-Cookie` (their own login/refresh response) is
 * ALSO echoed in that response's JSON body. It grants no extra access, cannot be used to read
 * another session's token, and does not widen what the cookie itself already grants — it only
 * changes the transport of a value the caller already legitimately owns.
 */
object ClientPlatform {
	const val HEADER_NAME = "X-Client-Platform"
	private const val MOBILE_VALUE = "mobile"

	fun isMobile(headerValue: String?): Boolean = headerValue.equals(MOBILE_VALUE, ignoreCase = true)
}

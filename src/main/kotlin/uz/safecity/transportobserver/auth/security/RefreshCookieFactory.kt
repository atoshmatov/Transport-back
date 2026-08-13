package uz.safecity.transportobserver.auth.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Builds the `Set-Cookie` header for the opaque refresh token issued by [RefreshTokenService].
 *
 * The refresh token used to travel in the JSON response body, which meant the SPA had to keep
 * it in memory (Pinia) to avoid the XSS exposure of `localStorage` — but that in turn meant a
 * plain page reload (F5) wiped it and bounced the user back to `/login` even though the Redis
 * session was still perfectly valid. Moving the refresh token into an HttpOnly cookie fixes
 * both problems at once: JS never sees the token (no XSS exposure) AND the browser re-sends it
 * automatically on reload (no more "reload kicks me to login").
 *
 * Cookie contract (frontend must switch its Axios client to `withCredentials: true` for this
 * to actually reach the browser's cookie jar):
 *  - name: [COOKIE_NAME]
 *  - `HttpOnly`: always — never readable from JS.
 *  - `Secure`: [cookieSecure] — must be `true` in production (HTTPS-only); `false` in local dev
 *    (`http://localhost`) since browsers reject `Secure` cookies over plain HTTP.
 *  - `SameSite=Lax`: sufficient here because the admin panel and API are same-site in practice
 *    (or a configured CORS origin, never a third-party site initiating the request).
 *  - `Path=/api/v1/auth`: the cookie is only ever sent back on auth endpoints (refresh/logout),
 *    not on every API call — keeps it out of unrelated request headers.
 *  - `Max-Age`: mirrors `security.jwt.refresh-token-ttl-seconds` on issue, `0` on [clear].
 */
@Component
class RefreshCookieFactory(
	@Value("\${security.jwt.refresh-token-ttl-seconds}") private val refreshTokenTtlSeconds: Long,
	@Value("\${app.security.cookie-secure:false}") private val cookieSecure: Boolean
) {

	companion object {
		const val COOKIE_NAME = "refreshToken"
		private const val COOKIE_PATH = "/api/v1/auth"
	}

	fun create(token: String): ResponseCookie = baseBuilder(token)
		.maxAge(Duration.ofSeconds(refreshTokenTtlSeconds))
		.build()

	/** Sent on logout (and could be reused on any hard auth failure) to make the browser drop the cookie. */
	fun clear(): ResponseCookie = baseBuilder("")
		.maxAge(Duration.ZERO)
		.build()

	private fun baseBuilder(value: String) = ResponseCookie.from(COOKIE_NAME, value)
		.httpOnly(true)
		.secure(cookieSecure)
		.sameSite("Lax")
		.path(COOKIE_PATH)
}

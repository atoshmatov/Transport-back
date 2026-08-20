package uz.safecity.transportobserver.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import uz.safecity.transportobserver.common.dto.ErrorResponse
import uz.safecity.transportobserver.common.exception.Messages
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Rejects `POST /api/v1/auth/login` with `429 Too Many Requests` once an IP has exceeded
 * [LoginRateLimiter]'s per-window budget — closes the IP-level brute-force gap left by the
 * per-account lockout in [uz.safecity.transportobserver.auth.service.AuthService.login] (see
 * [LoginRateLimiter] kdoc for the full reasoning).
 *
 * Deliberately scoped to exactly the login path/method — every other endpoint (including
 * `/auth/refresh`, `/auth/logout`) is unaffected. Runs before [JwtAuthenticationFilter] (wired in
 * [SecurityConfig]) so a rate-limited request never even reaches token parsing / DB lookups.
 *
 * Writes the JSON error response directly (same pattern as [PasswordChangeRequiredFilter]) rather
 * than throwing an [uz.safecity.transportobserver.common.exception.ApiException]: this filter
 * runs in the servlet filter chain, outside `@RestControllerAdvice`'s reach.
 */
@Component
class LoginRateLimitFilter(
	private val loginRateLimiter: LoginRateLimiter,
	private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain
	) {
		if (request.method != HttpMethod.POST.name() || request.requestURI != LOGIN_PATH) {
			filterChain.doFilter(request, response)
			return
		}

		val ip = clientIp(request)
		if (!loginRateLimiter.tryAcquire(ip)) {
			response.status = HttpStatus.TOO_MANY_REQUESTS.value()
			response.contentType = MediaType.APPLICATION_JSON_VALUE
			response.writer.write(
				objectMapper.writeValueAsString(
					ErrorResponse(
						code = "TOO_MANY_REQUESTS",
						message = Messages.resolve("error.auth.rate-limited")
					)
				)
			)
			return
		}

		filterChain.doFilter(request, response)
	}

	/**
	 * `X-Forwarded-For` first, since this app is expected to sit behind a reverse proxy/load
	 * balancer in any real deployment (same assumption other reverse-proxy-aware code in this repo
	 * makes); falls back to the raw socket address for local/dev where there's no proxy in front.
	 * Trusting a client-supplied header for rate-limiting is a deliberate MVP trade-off — a
	 * malicious client sitting directly in front of this app (no proxy) could spoof it to smear
	 * attempts across fake IPs, but that requires network access this app isn't expected to be
	 * exposed to directly in production (see `application-prod.yml`/deployment notes).
	 */
	private fun clientIp(request: HttpServletRequest): String {
		val forwardedFor = request.getHeader("X-Forwarded-For")
		return if (!forwardedFor.isNullOrBlank()) {
			forwardedFor.split(",").first().trim()
		} else {
			request.remoteAddr
		}
	}

	companion object {
		private const val LOGIN_PATH = "/api/v1/auth/login"
	}
}

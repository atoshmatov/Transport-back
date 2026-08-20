package uz.safecity.transportobserver.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import uz.safecity.transportobserver.common.dto.ErrorResponse
import uz.safecity.transportobserver.common.exception.Messages
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
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
	private val objectMapper: ObjectMapper,
	@Value("\${security.rate-limit.trust-forwarded-header:false}") private val trustForwardedHeader: Boolean
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
	 * Trusts the client-supplied `X-Forwarded-For` header only when
	 * `security.rate-limit.trust-forwarded-header` is explicitly enabled — that flag must only be
	 * turned on once this app actually sits behind a reverse proxy/load balancer that sets (or
	 * overwrites) the header itself. By default it's `false`, so this always falls back to
	 * [HttpServletRequest.getRemoteAddr] — the real TCP peer address, which a client cannot spoof.
	 * Trusting `X-Forwarded-For` unconditionally would let any direct client smear brute-force
	 * attempts across fake IPs and bypass [LoginRateLimiter] entirely, since there's no guarantee
	 * this app is deployed behind a proxy that validates/rewrites that header.
	 */
	private fun clientIp(request: HttpServletRequest): String {
		if (trustForwardedHeader) {
			val forwardedFor = request.getHeader("X-Forwarded-For")
			if (!forwardedFor.isNullOrBlank()) {
				return forwardedFor.split(",").first().trim()
			}
		}
		return request.remoteAddr
	}

	companion object {
		private const val LOGIN_PATH = "/api/v1/auth/login"
	}
}

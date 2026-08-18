package uz.safecity.transportobserver.auth.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Reads `Authorization: Bearer <accessToken>`, validates it via [JwtService],
 * and populates the SecurityContext. Missing/invalid token simply means the
 * request continues unauthenticated — SecurityConfig's authorizeHttpRequests
 * rules decide whether that's allowed.
 *
 * Also stamps [PresenceTracker] on every successfully-authenticated request (any role, web
 * or mobile) — this is the "session activity" half of the online/presence signal; see that
 * class's kdoc.
 */
@Component
class JwtAuthenticationFilter(
	private val jwtService: JwtService,
	private val userDetailsService: UserDetailsService,
	private val presenceTracker: PresenceTracker
) : OncePerRequestFilter() {

	private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain
	) {
		val header = request.getHeader("Authorization")
		if (header == null || !header.startsWith("Bearer ")) {
			filterChain.doFilter(request, response)
			return
		}

		val token = header.substringAfter("Bearer ").trim()
		val claims = jwtService.parse(token)
		// "username" claim missing/wrong-typed (e.g. a malformed or tampered-but-still-validly-signed
		// token) is treated the same as a missing/invalid token: request continues unauthenticated
		// and downstream authorization decides 401/403 — never a 500 from an unchecked cast.
		val username = claims?.get("username") as? String

		if (username != null && SecurityContextHolder.getContext().authentication == null) {
			val userDetails = userDetailsService.loadUserByUsername(username)

			if (userDetails.isEnabled && userDetails.isAccountNonLocked) {
				val authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
				authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
				SecurityContextHolder.getContext().authentication = authToken

				// Presence tracking is a best-effort side effect of authentication, never a
				// precondition for it — a DB hiccup here (e.g. connection pool exhaustion) must
				// not turn every authenticated request in the app into a 500.
				if (userDetails is CustomUserDetails) {
					try {
						presenceTracker.touch(userDetails.accountId)
					} catch (ex: Exception) {
						log.warn("Presence touch failed for accountId={}", userDetails.accountId, ex)
					}
				}
			}
		}

		filterChain.doFilter(request, response)
	}
}

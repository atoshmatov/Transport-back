package uz.safecity.transportobserver.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import uz.safecity.transportobserver.common.dto.ErrorResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Product decision (2026-08-12): forcing a password change right after login used
 * to 403 every endpoint except the `/auth/` ones whenever `mustChangePassword = true`,
 * effectively trapping the user on the change-password screen. That enforcement
 * has been made OPTIONAL — `mustChangePassword` is still returned in the login
 * response / stored on the account for the frontend to show an optional reminder,
 * and `POST /auth/change-password` still works whenever the user chooses to use
 * it, but nothing blocks the rest of the API anymore by default.
 *
 * Enforcement can be turned back on (e.g. if a future compliance requirement
 * needs it) via `app.security.enforce-password-change=true` without touching
 * this class again.
 *
 * Runs after [JwtAuthenticationFilter] so the principal is already resolved.
 */
@Component
@Order(1)
class PasswordChangeRequiredFilter(
	private val objectMapper: ObjectMapper,
	@Value("\${app.security.enforce-password-change:false}")
	private val enforcePasswordChange: Boolean
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain
	) {
		if (!enforcePasswordChange) {
			filterChain.doFilter(request, response)
			return
		}

		val principal = SecurityContextHolder.getContext().authentication?.principal as? CustomUserDetails

		val requiresChange = principal?.mustChangePassword == true
		// Everything under /auth/** stays reachable (change-password, logout, refresh, login).
		val isAuthPath = request.requestURI.startsWith("/api/v1/auth/")

		if (requiresChange && !isAuthPath) {
			response.status = HttpStatus.FORBIDDEN.value()
			response.contentType = MediaType.APPLICATION_JSON_VALUE
			response.writer.write(
				objectMapper.writeValueAsString(
					ErrorResponse(code = "PASSWORD_CHANGE_REQUIRED", message = "Avval parolni almashtirish kerak")
				)
			)
			return
		}

		filterChain.doFilter(request, response)
	}
}

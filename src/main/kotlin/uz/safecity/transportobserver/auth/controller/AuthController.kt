package uz.safecity.transportobserver.auth.controller

import uz.safecity.transportobserver.auth.dto.ChangePasswordRequest
import uz.safecity.transportobserver.auth.dto.LoginRequest
import uz.safecity.transportobserver.auth.dto.LoginResponse
import uz.safecity.transportobserver.auth.dto.LogoutRequest
import uz.safecity.transportobserver.auth.dto.RefreshRequest
import uz.safecity.transportobserver.auth.dto.RefreshResponse
import uz.safecity.transportobserver.auth.dto.ResetPasswordRequest
import uz.safecity.transportobserver.auth.dto.ResetPasswordResponse
import uz.safecity.transportobserver.auth.security.ClientPlatform
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.auth.security.RefreshCookieFactory
import uz.safecity.transportobserver.auth.service.AuthService
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.common.exception.RefreshTokenInvalidException
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The refresh token always travels as an HttpOnly cookie set/read via [RefreshCookieFactory] — see
 * that class's kdoc for the full cookie contract and why (fixes the "reload kicks me to /login" bug:
 * the old in-memory-only refresh token was wiped by a page reload even though the Redis session was
 * still valid). For the native mobile app specifically, it is ALSO echoed in the JSON response/
 * accepted from the JSON request body — see [ClientPlatform] kdoc for why (no cookie jar on
 * Kotlin/Native's Ktor `HttpClient`) and [uz.safecity.transportobserver.auth.dto.LoginResponse] kdoc
 * for the wire contract. Web behavior (cookie-only, never in the body) is unchanged.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
	private val authService: AuthService,
	private val refreshCookieFactory: RefreshCookieFactory
) {

	@PostMapping("/login")
	fun login(
		@Valid @RequestBody request: LoginRequest,
		@RequestHeader(name = ClientPlatform.HEADER_NAME, required = false) clientPlatform: String?
	): ResponseEntity<ApiResponse<LoginResponse>> {
		val (response, refreshToken) = authService.login(request)
		val body = if (ClientPlatform.isMobile(clientPlatform)) response.copy(refreshToken = refreshToken) else response
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(refreshToken).toString())
			.body(ApiResponse.ok(body))
	}

	@PostMapping("/refresh")
	fun refresh(
		@CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) cookieRefreshToken: String?,
		@RequestBody(required = false) request: RefreshRequest?,
		@RequestHeader(name = ClientPlatform.HEADER_NAME, required = false) clientPlatform: String?
	): ResponseEntity<ApiResponse<RefreshResponse>> {
		val isMobile = ClientPlatform.isMobile(clientPlatform)
		// Mobile has no cookie jar (see ClientPlatform kdoc) so its own body-supplied token takes
		// priority when the header says mobile; web never sends a body, so it always falls through
		// to the cookie. Neither source alone (missing cookie AND missing/non-mobile body) resolves
		// a token — same 401 contract as an unresolvable/expired one, decided in AuthService.refresh.
		val token = (request?.refreshToken.takeIf { isMobile }) ?: cookieRefreshToken ?: throw RefreshTokenInvalidException()
		val (response, newRefreshToken) = authService.refresh(token)
		val body = if (isMobile) response.copy(refreshToken = newRefreshToken) else response
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(newRefreshToken).toString())
			.body(ApiResponse.ok(body))
	}

	@PostMapping("/logout")
	fun logout(
		@CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) cookieRefreshToken: String?,
		@RequestBody(required = false) request: LogoutRequest?
	): ResponseEntity<Void> {
		// Web has the cookie; mobile (no cookie jar, see ClientPlatform kdoc) sends the token in the
		// body instead — no platform header check needed here since the response never differs.
		authService.logout(cookieRefreshToken ?: request?.refreshToken)
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
			.build()
	}

	@PostMapping("/change-password")
	fun changePassword(
		@AuthenticationPrincipal principal: CustomUserDetails,
		@Valid @RequestBody request: ChangePasswordRequest
	): ResponseEntity<Void> {
		authService.changePassword(principal.accountId, request)
		return ResponseEntity.noContent().build()
	}

	/**
	 * ADMIN/SUPER_ADMIN only — enforced both here and in SecurityConfig's authorizeHttpRequests.
	 * That is only the coarse "may call this endpoint at all" gate; the actor-vs-target role
	 * hierarchy (an ADMIN may not reset another ADMIN's or SUPER_ADMIN's password) is enforced
	 * inside [AuthService.resetPassword] via `RoleHierarchyGuard` — see that method's kdoc.
	 */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
	@PostMapping("/reset-password")
	fun resetPassword(
		@Valid @RequestBody request: ResetPasswordRequest,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<ResetPasswordResponse>> =
		ResponseEntity.status(HttpStatus.OK).body(
			ApiResponse.ok(authService.resetPassword(request.accountId, principal.accountId, principal.role))
		)
}

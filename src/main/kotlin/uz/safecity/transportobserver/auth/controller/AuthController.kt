package uz.safecity.transportobserver.auth.controller

import uz.safecity.transportobserver.auth.dto.ChangePasswordRequest
import uz.safecity.transportobserver.auth.dto.LoginRequest
import uz.safecity.transportobserver.auth.dto.LoginResponse
import uz.safecity.transportobserver.auth.dto.RefreshResponse
import uz.safecity.transportobserver.auth.dto.ResetPasswordRequest
import uz.safecity.transportobserver.auth.dto.ResetPasswordResponse
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The refresh token never appears in a JSON response/request body here — it travels exclusively
 * as an HttpOnly cookie set/read via [RefreshCookieFactory]. See that class's kdoc for the full
 * cookie contract and why (fixes the "reload kicks me to /login" bug: the old in-memory-only
 * refresh token was wiped by a page reload even though the Redis session was still valid).
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
	private val authService: AuthService,
	private val refreshCookieFactory: RefreshCookieFactory
) {

	@PostMapping("/login")
	fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<LoginResponse>> {
		val (response, refreshToken) = authService.login(request)
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(refreshToken).toString())
			.body(ApiResponse.ok(response))
	}

	@PostMapping("/refresh")
	fun refresh(
		@CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) refreshToken: String?
	): ResponseEntity<ApiResponse<RefreshResponse>> {
		// No cookie at all (never logged in via this flow, or browser dropped it) — same 401
		// contract as an unresolvable/expired token, decided in AuthService.refresh.
		val token = refreshToken ?: throw RefreshTokenInvalidException()
		val (response, newRefreshToken) = authService.refresh(token)
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(newRefreshToken).toString())
			.body(ApiResponse.ok(response))
	}

	@PostMapping("/logout")
	fun logout(
		@CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) refreshToken: String?
	): ResponseEntity<Void> {
		authService.logout(refreshToken)
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

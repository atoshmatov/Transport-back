package uz.safecity.transportobserver.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.ClientPlatform
import uz.safecity.transportobserver.auth.security.RefreshCookieFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the mobile-vs-web split on `POST /api/v1/auth/{login,refresh}` fixed alongside this test
 * class: the native mobile app's Ktor `HttpClient` has no cookie jar, so `LoginResponse`/
 * `RefreshResponse` never carrying `refreshToken` in the body (cookie-only, see [RefreshCookieFactory]
 * kdoc) made every mobile login crash client-side with `MissingFieldException` — the mobile DTO
 * requires that field. [ClientPlatform] lets the mobile app opt in via a request header; web
 * requests (which never send it) must see byte-for-byte unchanged behavior — refreshToken absent
 * from the JSON body, present only as the HttpOnly cookie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var passwordEncoder: PasswordEncoder

	private fun createAccount(username: String, password: String): Account =
		accountRepository.save(
			Account(
				username = username,
				passwordHash = passwordEncoder.encode(password),
				role = RoleType.INSPECTOR,
				mustChangePassword = false,
				isActive = true
			)
		)

	private fun uniqueUsername(): String = "authtest_${UUID.randomUUID().toString().take(20)}"

	@Test
	fun `web login (no platform header) never gets refreshToken in the body, only the cookie`() {
		val password = "Original123!"
		createAccount(uniqueUsername(), password).let { account ->
			mockMvc.perform(
				post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(mapOf("username" to account.username, "password" to password)))
			)
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.data.accessToken").exists())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("${RefreshCookieFactory.COOKIE_NAME}=")))
		}
	}

	@Test
	fun `mobile login (X-Client-Platform mobile header) gets refreshToken in the body, plus the cookie`() {
		val password = "Original123!"
		val account = createAccount(uniqueUsername(), password)

		mockMvc.perform(
			post("/api/v1/auth/login")
				.header(ClientPlatform.HEADER_NAME, "mobile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mapOf("username" to account.username, "password" to password)))
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.accessToken").exists())
			.andExpect(jsonPath("$.data.refreshToken").isString)
			.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("${RefreshCookieFactory.COOKIE_NAME}=")))
	}

	@Test
	fun `mobile refresh accepts the refresh token from the JSON body and returns a new one in the body`() {
		val password = "Original123!"
		val account = createAccount(uniqueUsername(), password)

		val loginResult = mockMvc.perform(
			post("/api/v1/auth/login")
				.header(ClientPlatform.HEADER_NAME, "mobile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mapOf("username" to account.username, "password" to password)))
		)
			.andExpect(status().isOk)
			.andReturn()

		val loginBody = objectMapper.readTree(loginResult.response.contentAsString)
		val mobileRefreshToken = loginBody["data"]["refreshToken"].asText()

		// No cookie forwarded at all — proves the mobile flow works purely off the JSON-body token,
		// exactly like the real Ktor client (no cookie jar installed).
		mockMvc.perform(
			post("/api/v1/auth/refresh")
				.header(ClientPlatform.HEADER_NAME, "mobile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mapOf("refreshToken" to mobileRefreshToken)))
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.accessToken").exists())
			.andExpect(jsonPath("$.data.refreshToken").isString)
	}

	@Test
	fun `web refresh with no cookie and no body is rejected as invalid`() {
		mockMvc.perform(post("/api/v1/auth/refresh"))
			.andExpect(status().isUnauthorized)
	}

	@Test
	fun `a body-supplied refresh token is ignored without the mobile header`() {
		val password = "Original123!"
		val account = createAccount(uniqueUsername(), password)

		val loginResult = mockMvc.perform(
			post("/api/v1/auth/login")
				.header(ClientPlatform.HEADER_NAME, "mobile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mapOf("username" to account.username, "password" to password)))
		)
			.andExpect(status().isOk)
			.andReturn()

		val loginBody = objectMapper.readTree(loginResult.response.contentAsString)
		val mobileRefreshToken = loginBody["data"]["refreshToken"].asText()

		// Same valid token, but no X-Client-Platform header and no cookie — must NOT be honored via
		// the body, since a web caller should only ever be authenticated by the cookie.
		mockMvc.perform(
			post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(mapOf("refreshToken" to mobileRefreshToken)))
		)
			.andExpect(status().isUnauthorized)
	}
}

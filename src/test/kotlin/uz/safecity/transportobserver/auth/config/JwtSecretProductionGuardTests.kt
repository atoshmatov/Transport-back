package uz.safecity.transportobserver.auth.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Plain unit tests (no Spring context needed — [JwtSecretProductionGuard] only depends on a
 * constructor-injected `String`) for the KRITIK fail-fast fix: `prod`/`production` must never be
 * able to start with `security.jwt.secret` left at the checked-in dev placeholder, since that
 * value is public (readable in this repo) and would let anyone forge a token for any role.
 */
class JwtSecretProductionGuardTests {

	@Test
	fun `startup fails when jwt secret is still the checked-in dev default`() {
		val guard = JwtSecretProductionGuard(jwtSecret = JwtSecretProductionGuard.DEFAULT_JWT_SECRET)

		val ex = assertThrows(IllegalStateException::class.java) {
			guard.verifyJwtSecretIsNotDefault()
		}
		assert(ex.message?.contains("security.jwt.secret") == true)
	}

	@Test
	fun `startup fails when jwt secret is shorter than the minimum safe length`() {
		val guard = JwtSecretProductionGuard(jwtSecret = "too-short-secret")

		assertThrows(IllegalStateException::class.java) {
			guard.verifyJwtSecretIsNotDefault()
		}
	}

	@Test
	fun `startup succeeds with a real, sufficiently long, non-default secret`() {
		val guard = JwtSecretProductionGuard(
			jwtSecret = "a-real-randomly-generated-production-secret-value-not-the-default"
		)

		assertDoesNotThrow { guard.verifyJwtSecretIsNotDefault() }
	}
}

package uz.safecity.transportobserver.auth.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Fail-fast guard against the single most dangerous JWT config mistake: deploying to production
 * with `security.jwt.secret` still resolving to the checked-in dev placeholder
 * (`application.yml`: `secret: \${JWT_SECRET:CHANGE_ME_dev_only_32_bytes_minimum_secret_key}`).
 *
 * That placeholder is visible to anyone who can read this repository. If `JWT_SECRET` is not set
 * in a production environment, [uz.safecity.transportobserver.auth.security.JwtService] silently
 * signs every access token with this public string — anyone can forge a token for any account,
 * including `ROLE_SUPER_ADMIN`, entirely offline. Nothing else breaks; the app just quietly runs
 * insecure. Same "silent fail-open" shape as the cookie-secure gap
 * [ProductionCookieSecurityGuard] closes, so this follows the exact same pattern: re-check the
 * *actual resolved* value at startup under the `prod`/`production` profile and refuse to finish
 * starting rather than run with a known-public signing key.
 *
 * Unlike `app.security.cookie-secure` (a plain boolean with a safe/unsafe pole),
 * `security.jwt.secret` has no dedicated hardcoded override in `application-prod.yml` — the
 * fix here is comparing the resolved value against the known default string itself, not a second
 * profile-specific YAML value.
 *
 * Scoped to `prod`/`production` only via `@Profile`, same reasoning as [ProductionCookieSecurityGuard]:
 * the placeholder is expected and harmless under `dev`/tests.
 */
@Component
@Profile("prod", "production")
class JwtSecretProductionGuard(
	@Value("\${security.jwt.secret}") private val jwtSecret: String
) {

	private val log = LoggerFactory.getLogger(JwtSecretProductionGuard::class.java)

	@PostConstruct
	fun verifyJwtSecretIsNotDefault() {
		check(jwtSecret != DEFAULT_JWT_SECRET) {
			"security.jwt.secret 'prod'/'production' profilida standart (repo'da oshkora) qiymat " +
				"bilan ISHLASHI MUMKIN EMAS — bu holda istalgan kishi shu ma'lum kalit bilan " +
				"o'zi uchun istalgan rolda (jumladan ROLE_SUPER_ADMIN) token soxtalashtirishi mumkin. " +
				"JWT_SECRET environment-variable orqali kamida 32 baytli, tasodifiy generatsiya " +
				"qilingan maxfiy qiymat bering."
		}
		check(jwtSecret.toByteArray(Charsets.UTF_8).size >= MIN_SECRET_BYTES) {
			"security.jwt.secret 'prod'/'production' profilida kamida $MIN_SECRET_BYTES baytdan " +
				"iborat bo'lishi kerak (HMAC-SHA imzolash uchun minimal xavfsiz uzunlik). " +
				"JWT_SECRET qiymatini uzunroq, tasodifiy qatorga almashtiring."
		}
		log.info("Production JWT-secret guard OK: security.jwt.secret standart qiymatda emas.")
	}

	companion object {
		/** Must stay byte-for-byte identical to application.yml's `JWT_SECRET` default. */
		const val DEFAULT_JWT_SECRET = "CHANGE_ME_dev_only_32_bytes_minimum_secret_key"
		private const val MIN_SECRET_BYTES = 32
	}
}

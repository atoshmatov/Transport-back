package uz.safecity.transportobserver.auth.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Fail-fast guard against the single most dangerous operator mistake in this app's config:
 * deploying to production with the refresh-token cookie's `Secure` flag off.
 *
 * `app.security.cookie-secure` (see [uz.safecity.transportobserver.auth.security.RefreshCookieFactory])
 * controls the `Secure` attribute on the HttpOnly refresh-token cookie. If it resolves to `false`
 * while this app is running under the `prod`/`production` profile, the refresh token would be sent
 * back to the browser over plain HTTP too and could be intercepted on the wire — a silent "fail
 * open": nothing else breaks, the app just quietly serves a cookie without the one attribute that
 * makes it safe to use outside local dev.
 *
 * `application-prod.yml` already hardcodes `cookie-secure: true` with no `${COOKIE_SECURE:...}`
 * env placeholder, which closes the realistic case (operator forgets to set `COOKIE_SECURE`). This
 * class is the second, independent line of defense: it re-checks the *actual resolved* value at
 * startup — so even an unusual override path (e.g. someone setting the relaxed-binding env var
 * `APP_SECURITY_COOKIE_SECURE=false` directly, which outranks the YAML files) still gets caught —
 * and refuses to let the application finish starting instead of silently running insecure.
 *
 * Scoped to `prod`/`production` only via `@Profile` so it never fires (and never even gets
 * instantiated) for `dev` or tests, where an insecure cookie over plain `http://localhost` is
 * expected and correct.
 */
@Component
@Profile("prod", "production")
class ProductionCookieSecurityGuard(
	@Value("\${app.security.cookie-secure:false}") private val cookieSecure: Boolean
) {

	private val log = LoggerFactory.getLogger(ProductionCookieSecurityGuard::class.java)

	@PostConstruct
	fun verifyCookieSecureIsEnabled() {
		check(cookieSecure) {
			"app.security.cookie-secure=false 'prod'/'production' profilida ISHLASHI MUMKIN EMAS " +
				"— HttpOnly refresh-token cookie 'Secure' bayrog'isiz oddiy HTTP orqali ham " +
				"yuboriladi va tarmoqda ushlab olinishi mumkin. COOKIE_SECURE=true qiling yoki " +
				"app.security.cookie-secure ni to'g'ridan-to'g'ri true qilib override qilgan " +
				"joyni tekshiring (application-prod.yml da hardcoded true bo'lishi kerak)."
		}
		log.info("Production cookie-security guard OK: app.security.cookie-secure=true.")
	}
}

package uz.safecity.transportobserver.auth.config

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Dev-only bootstrap seed.
 *
 * Per TZ (see [Account] kdoc), accounts are never self-registered — only an
 * admin can create one. That's correct for production, but it also means a
 * fresh database has zero rows in `accounts`, so nobody can log in at all
 * (chicken-and-egg problem for the very first admin).
 *
 * This runner breaks that deadlock **only in the `dev` profile**: if
 * `accounts` is completely empty when the app starts, it creates exactly one
 * SUPER_ADMIN account so a developer can log in and start provisioning real
 * accounts through the normal admin flow. It never runs again once any
 * account exists (including the one it just created), and `@Profile("dev")`
 * keeps it out of every other profile — there is no equivalent for `prod`,
 * intentionally: a production database must be seeded manually/out-of-band,
 * never by an auto-run bean.
 *
 * The generated password still goes through the same first-login policy as
 * every other account (`mustChangePassword = true`), so it isn't a
 * permanently valid credential — it exists only long enough to log in once
 * and set a real password via `POST /api/v1/auth/change-password`.
 */
@Component
@Profile("dev")
class DevBootstrapAdminSeeder(
	private val accountRepository: AccountRepository,
	private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(DevBootstrapAdminSeeder::class.java)

	companion object {
		const val BOOTSTRAP_USERNAME = "admin"

		// Dev-only bootstrap credential. Meets the same 8-72 char policy as
		// ChangePasswordRequest; mustChangePassword=true forces it to be
		// replaced on first login, so it is never a long-lived secret.
		const val BOOTSTRAP_PASSWORD = "Admin@12345"
	}

	override fun run(args: ApplicationArguments) {
		if (accountRepository.count() > 0) {
			// Not just "does 'admin' exist" — any pre-existing account means
			// this DB is no longer a blank slate (e.g. real dev data was
			// loaded), so stay out of the way entirely.
			return
		}

		val bootstrapAdmin = Account(
			username = BOOTSTRAP_USERNAME,
			passwordHash = passwordEncoder.encode(BOOTSTRAP_PASSWORD),
			role = RoleType.SUPER_ADMIN
		)
		accountRepository.save(bootstrapAdmin)

		log.warn(
			"accounts jadvali bo'sh edi — dev bootstrap SUPER_ADMIN yaratildi: " +
				"username='{}', password='{}' (birinchi kirishda almashtirish talab qilinadi). " +
				"Bu FAQAT 'dev' profilida ishlaydi, productionda ishlamaydi.",
			BOOTSTRAP_USERNAME,
			BOOTSTRAP_PASSWORD
		)
	}
}

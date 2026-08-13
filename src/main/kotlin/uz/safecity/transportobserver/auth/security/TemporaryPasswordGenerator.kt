package uz.safecity.transportobserver.auth.security

import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * Generates one-time temporary passwords for admin-provisioned accounts.
 * Shared by [uz.safecity.transportobserver.auth.service.AuthService.resetPassword]
 * and [uz.safecity.transportobserver.employees.service.EmployeeService] (create /
 * reset-password flows) so every admin-issued credential has the same
 * entropy/character set — ambiguous-looking characters (0/O, 1/l/I) are
 * excluded so a human can read the password out loud/type it correctly.
 */
@Component
class TemporaryPasswordGenerator {

	private val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
	private val random = SecureRandom()

	fun generate(length: Int = 10): String =
		(1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
}

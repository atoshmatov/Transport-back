package uz.safecity.transportobserver.auth.security

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

/**
 * Spring Security principal backed by [Account]. Carries `mustChangePassword`
 * so [uz.safecity.transportobserver.auth.security.PasswordChangeRequiredFilter]
 * can lock the caller down to /auth/change-password only. Also carries [role]
 * directly (not just as a [GrantedAuthority]) so services can enforce
 * role-hierarchy rules (e.g. EmployeeService's ADMIN-cannot-manage-ADMIN/SUPER_ADMIN
 * check) without re-parsing the "ROLE_" authority string.
 */
class CustomUserDetails(
	val accountId: UUID,
	val role: RoleType,
	private val username: String,
	private val passwordHash: String,
	private val authorities: List<GrantedAuthority>,
	val mustChangePassword: Boolean,
	private val enabled: Boolean,
	private val accountNonLocked: Boolean
) : UserDetails {

	companion object {
		fun from(account: Account): CustomUserDetails = CustomUserDetails(
			accountId = requireNotNull(account.id),
			role = account.role,
			username = account.username,
			passwordHash = account.passwordHash,
			authorities = listOf(SimpleGrantedAuthority(account.role.authority)),
			mustChangePassword = account.mustChangePassword,
			enabled = account.isActive,
			accountNonLocked = !account.isCurrentlyLocked()
		)
	}

	override fun getAuthorities(): Collection<GrantedAuthority> = authorities
	override fun getPassword(): String = passwordHash
	override fun getUsername(): String = username
	override fun isAccountNonExpired(): Boolean = true
	override fun isAccountNonLocked(): Boolean = accountNonLocked
	override fun isCredentialsNonExpired(): Boolean = true
	override fun isEnabled(): Boolean = enabled
}

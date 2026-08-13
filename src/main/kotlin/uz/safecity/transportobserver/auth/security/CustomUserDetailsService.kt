package uz.safecity.transportobserver.auth.security

import uz.safecity.transportobserver.auth.repository.AccountRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
	private val accountRepository: AccountRepository
) : UserDetailsService {

	override fun loadUserByUsername(username: String): UserDetails {
		val account = accountRepository.findByUsername(username)
			.orElseThrow { UsernameNotFoundException("Account not found: $username") }
		return CustomUserDetails.from(account)
	}
}

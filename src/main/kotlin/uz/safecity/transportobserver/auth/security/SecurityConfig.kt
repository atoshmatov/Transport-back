package uz.safecity.transportobserver.auth.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableMethodSecurity
class SecurityConfig(
	private val jwtAuthenticationFilter: JwtAuthenticationFilter,
	private val passwordChangeRequiredFilter: PasswordChangeRequiredFilter,
	private val loginRateLimitFilter: LoginRateLimitFilter,
	private val userDetailsService: CustomUserDetailsService,
	// Comma-separated explicit origin list (app.cors.allowed-origins) — NOT a wildcard. Cookie-based
	// refresh-token auth requires allowCredentials(true), and browsers reject the combination of
	// allowCredentials + a wildcard origin, so this must always be a concrete list of origins
	// (dev: the Vite dev server; prod: the real admin-panel origin(s)).
	@Value("\${app.cors.allowed-origins}")
	private val allowedOriginsConfig: String
) {

	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

	@Bean
	fun authenticationProvider(): DaoAuthenticationProvider {
		val provider = DaoAuthenticationProvider()
		provider.setUserDetailsService(userDetailsService)
		provider.setPasswordEncoder(passwordEncoder())
		return provider
	}

	@Bean
	fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
		config.authenticationManager

	@Bean
	fun corsConfigurationSource(): CorsConfigurationSource {
		val config = CorsConfiguration().apply {
			allowedOrigins = allowedOriginsConfig.split(",").map { it.trim() }.filter { it.isNotEmpty() }
			allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			allowedHeaders = listOf("*")
			// Required for the HttpOnly refresh-token cookie (RefreshCookieFactory) to be sent/accepted
			// cross-origin by the browser — MUST stay paired with an explicit allowedOrigins list above,
			// never "*"/allowedOriginPatterns("*"), or browsers silently drop the cookie.
			allowCredentials = true
		}
		return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
	}

	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.cors { it.configurationSource(corsConfigurationSource()) }
			.csrf { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests { authorize ->
				authorize
					.requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
					.requestMatchers("/actuator/health", "/actuator/info").permitAll()
					.requestMatchers("/ws/**").permitAll()
					.requestMatchers("/api/v1/auth/reset-password").hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN")
					.anyRequest().authenticated()
			}
			.authenticationProvider(authenticationProvider())
			// JwtAuthenticationFilter must be registered (addFilterBefore against a known Spring
			// Security filter class) before anything else can position itself relative to IT —
			// Spring Security's FilterOrderRegistration throws "does not have a registered order"
			// otherwise. So this stays first, and the new rate-limit filter is placed relative to
			// it afterward.
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
			// Rate-limit login attempts before anything else touches the request — see
			// LoginRateLimiter/LoginRateLimitFilter kdocs for why this sits ahead of JWT parsing.
			.addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter::class.java)
			.addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter::class.java)

		return http.build()
	}
}
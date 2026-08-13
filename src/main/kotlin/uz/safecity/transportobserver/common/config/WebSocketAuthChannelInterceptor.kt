package uz.safecity.transportobserver.common.config

import uz.safecity.transportobserver.auth.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component

/**
 * Enforces JWT auth on STOMP CONNECT frames.
 *
 * SecurityConfig `permitAll()`s the `/ws` handshake path at the HTTP layer because the
 * WebSocket/SockJS handshake itself carries no Authorization header the way
 * a normal REST call does — the actual login check has to happen at the STOMP
 * protocol level instead, on the first frame the client sends after the
 * socket is open. Without this interceptor, anyone who can reach the port
 * could subscribe to live vehicle/inspector locations and notifications with
 * no credentials at all.
 *
 * Client is expected to send the access token as a STOMP native header on
 * the CONNECT frame, e.g. (stomp.js): `client.connectHeaders = { Authorization: "Bearer <token>" }`.
 * A missing/invalid/expired token throws, which the STOMP sub-protocol
 * handler turns into an ERROR frame and closes the session — same fail-closed
 * behavior as the HTTP JwtAuthenticationFilter for protected REST endpoints.
 */
@Component
class WebSocketAuthChannelInterceptor(
	private val jwtService: JwtService,
	private val userDetailsService: UserDetailsService
) : ChannelInterceptor {

	private val log = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor::class.java)

	override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
		val accessor = StompHeaderAccessor.wrap(message)

		if (accessor.command == StompCommand.CONNECT) {
			val header = accessor.getFirstNativeHeader("Authorization")
				?: throw AuthenticationCredentialsNotFoundException("Missing Authorization header on STOMP CONNECT")

			if (!header.startsWith("Bearer ")) {
				throw AuthenticationCredentialsNotFoundException("Malformed Authorization header on STOMP CONNECT")
			}

			val token = header.substringAfter("Bearer ").trim()
			val claims = jwtService.parse(token)
				?: throw AuthenticationCredentialsNotFoundException("Invalid or expired STOMP CONNECT token")

			val username = claims["username"] as? String
				?: throw AuthenticationCredentialsNotFoundException("Token missing username claim")

			val userDetails = try {
				userDetailsService.loadUserByUsername(username)
			} catch (ex: UsernameNotFoundException) {
				throw AuthenticationCredentialsNotFoundException("Unknown account for STOMP CONNECT", ex)
			}

			if (!userDetails.isEnabled || !userDetails.isAccountNonLocked) {
				throw AuthenticationCredentialsNotFoundException("Account disabled or locked")
			}

			val authToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
			accessor.user = authToken
			log.debug("STOMP CONNECT authenticated for account={}", username)
		}

		return message
	}
}

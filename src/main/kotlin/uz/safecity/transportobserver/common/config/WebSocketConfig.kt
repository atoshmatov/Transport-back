package uz.safecity.transportobserver.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * STOMP over WebSocket for real-time features: live vehicle/inspector
 * locations on the `map` module and push-style `notifications`.
 *
 * Client connects to /ws (SockJS fallback), subscribes under /topic and
 * /queue, and sends under /app. Broker relay (e.g. to RabbitMQ's STOMP
 * plugin) can replace the simple in-memory broker later if we need to
 * scale horizontally.
 *
 * Auth: SecurityConfig permits the `/ws` handshake path at the HTTP level, but
 * [WebSocketAuthChannelInterceptor] enforces the JWT check on the STOMP
 * CONNECT frame itself — see that class for why the check can't live in
 * SecurityConfig's HTTP filter chain.
 *
 * TODO(prod): `setAllowedOriginPatterns("*")` below is dev-only, same as
 * SecurityConfig.corsConfigurationSource — lock both down to the real
 * admin-panel/mobile origins before this goes near production.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
	private val webSocketAuthChannelInterceptor: WebSocketAuthChannelInterceptor
) : WebSocketMessageBrokerConfigurer {

	override fun configureMessageBroker(registry: MessageBrokerRegistry) {
		registry.enableSimpleBroker("/topic", "/queue")
		registry.setApplicationDestinationPrefixes("/app")
		registry.setUserDestinationPrefix("/user")
	}

	override fun registerStompEndpoints(registry: StompEndpointRegistry) {
		registry.addEndpoint("/ws")
			.setAllowedOriginPatterns("*") // TODO(prod): restrict to real origins
			.withSockJS()
	}

	override fun configureClientInboundChannel(registration: ChannelRegistration) {
		registration.interceptors(webSocketAuthChannelInterceptor)
	}
}

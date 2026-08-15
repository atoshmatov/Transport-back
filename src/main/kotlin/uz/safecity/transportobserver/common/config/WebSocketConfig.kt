package uz.safecity.transportobserver.common.config

import org.springframework.beans.factory.annotation.Value
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
 * Allowed origins share the same env-based whitelist (app.cors.allowed-origins /
 * CORS_ALLOWED_ORIGINS) as SecurityConfig.corsConfigurationSource, so the browser-facing
 * admin-panel origin only has to be configured in one place.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
	private val webSocketAuthChannelInterceptor: WebSocketAuthChannelInterceptor,
	@Value("\${app.cors.allowed-origins}")
	private val allowedOriginsConfig: String
) : WebSocketMessageBrokerConfigurer {

	override fun configureMessageBroker(registry: MessageBrokerRegistry) {
		registry.enableSimpleBroker("/topic", "/queue")
		registry.setApplicationDestinationPrefixes("/app")
		registry.setUserDestinationPrefix("/user")
	}

	override fun registerStompEndpoints(registry: StompEndpointRegistry) {
		val allowedOrigins = allowedOriginsConfig.split(",").map { it.trim() }.filter { it.isNotEmpty() }
		registry.addEndpoint("/ws")
			.setAllowedOrigins(*allowedOrigins.toTypedArray())
			.withSockJS()
	}

	override fun configureClientInboundChannel(registration: ChannelRegistration) {
		registration.interceptors(webSocketAuthChannelInterceptor)
	}
}

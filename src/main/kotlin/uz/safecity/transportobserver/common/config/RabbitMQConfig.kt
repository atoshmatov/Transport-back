package uz.safecity.transportobserver.common.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Async event bus between modules, e.g. `incidents` publishes an event that
 * `notifications` consumes to push a WebSocket/push notification, without a
 * direct compile-time dependency between the two modules.
 *
 * Placeholder topology — extend with real queues as each module's async flow
 * is implemented (incident.created, railsafe.alert, report.generated, ...).
 */
@Configuration
class RabbitMQConfig {

	companion object {
		const val EXCHANGE = "transport-observer.events"
		const val QUEUE_NOTIFICATIONS = "transport-observer.notifications"
		const val ROUTING_KEY_NOTIFICATIONS = "notification.#"
	}

	@Bean
	fun eventsExchange(): TopicExchange = TopicExchange(EXCHANGE)

	@Bean
	fun notificationsQueue(): Queue = Queue(QUEUE_NOTIFICATIONS, true)

	@Bean
	fun notificationsBinding(notificationsQueue: Queue, eventsExchange: TopicExchange): Binding =
		BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(ROUTING_KEY_NOTIFICATIONS)

	@Bean
	fun messageConverter(): MessageConverter = Jackson2JsonMessageConverter()
}

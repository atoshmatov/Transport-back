package uz.safecity.transportobserver.common.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
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

		/**
		 * Report PDF-export pipeline (see [uz.safecity.transportobserver.reports.service.ReportService.create]
		 * / [uz.safecity.transportobserver.reports.service.ReportGenerationListener]) — the queue this
		 * kdoc's original "report.generated" placeholder note pointed at, now wired up for real.
		 */
		const val QUEUE_REPORT_GENERATION = "transport-observer.reports.generation"
		const val ROUTING_KEY_REPORT_GENERATION = "report.generated"

		/**
		 * Dead-letter side of [QUEUE_REPORT_GENERATION]. Combined with the
		 * `spring.rabbitmq.listener.simple.retry.*` settings in `application.yml`: once a delivery
		 * exhausts its bounded retry attempts (or Spring AMQP rejects it for any other reason, e.g. a
		 * malformed/poison message that fails [ReportGenerationMessage] deserialization before
		 * [uz.safecity.transportobserver.reports.service.ReportGenerationListener.onMessage] is even
		 * invoked), the broker routes it here instead of redelivering it forever — see
		 * [uz.safecity.transportobserver.reports.service.ReportGenerationService.generate] kdoc for
		 * how that method's own try-catch avoids relying on this for its normal (business-level)
		 * failure handling.
		 */
		private const val DLX_EXCHANGE = "transport-observer.events.dlx"
		const val QUEUE_REPORT_GENERATION_DLQ = "transport-observer.reports.generation.dlq"
		private const val ROUTING_KEY_REPORT_GENERATION_DLQ = "report.generated.dlq"
	}

	@Bean
	fun eventsExchange(): TopicExchange = TopicExchange(EXCHANGE)

	@Bean
	fun notificationsQueue(): Queue = Queue(QUEUE_NOTIFICATIONS, true)

	@Bean
	fun notificationsBinding(notificationsQueue: Queue, eventsExchange: TopicExchange): Binding =
		BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(ROUTING_KEY_NOTIFICATIONS)

	@Bean
	fun reportGenerationQueue(): Queue =
		QueueBuilder.durable(QUEUE_REPORT_GENERATION)
			.withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
			.withArgument("x-dead-letter-routing-key", ROUTING_KEY_REPORT_GENERATION_DLQ)
			.build()

	@Bean
	fun reportGenerationBinding(reportGenerationQueue: Queue, eventsExchange: TopicExchange): Binding =
		BindingBuilder.bind(reportGenerationQueue).to(eventsExchange).with(ROUTING_KEY_REPORT_GENERATION)

	/** Dead-letter exchange for [QUEUE_REPORT_GENERATION] — kept separate from [eventsExchange] (a `TopicExchange`
	 * used for normal pub/sub routing) since dead-lettered messages only ever need one fixed routing key. */
	@Bean
	fun reportGenerationDlx(): DirectExchange = DirectExchange(DLX_EXCHANGE)

	@Bean
	fun reportGenerationDlq(): Queue = QueueBuilder.durable(QUEUE_REPORT_GENERATION_DLQ).build()

	@Bean
	fun reportGenerationDlqBinding(reportGenerationDlq: Queue, reportGenerationDlx: DirectExchange): Binding =
		BindingBuilder.bind(reportGenerationDlq).to(reportGenerationDlx).with(ROUTING_KEY_REPORT_GENERATION_DLQ)

	@Bean
	fun messageConverter(): MessageConverter = Jackson2JsonMessageConverter()
}

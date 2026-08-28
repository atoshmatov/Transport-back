package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.common.config.RabbitMQConfig
import uz.safecity.transportobserver.reports.dto.ReportGenerationMessage
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Consumes `report.generated` messages published by [ReportService.create] and delegates the
 * actual PDF build to [ReportGenerationService.generate]. Kept as its own thin `@Component`
 * (rather than putting `@RabbitListener` directly on [ReportGenerationService]) so
 * [ReportGenerationService.generate] stays a plain, directly-callable method — this is the only
 * class in the pipeline that couples the domain logic to Spring AMQP's message-driven invocation,
 * which keeps [ReportGenerationService]'s own test suite free of any broker dependency.
 */
@Component
class ReportGenerationListener(
	private val reportGenerationService: ReportGenerationService
) {

	@RabbitListener(queues = [RabbitMQConfig.QUEUE_REPORT_GENERATION])
	fun onMessage(message: ReportGenerationMessage) {
		reportGenerationService.generate(message.reportId)
	}
}

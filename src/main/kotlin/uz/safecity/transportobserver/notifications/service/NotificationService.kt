package uz.safecity.transportobserver.notifications.service

import uz.safecity.transportobserver.notifications.entity.Notification
import uz.safecity.transportobserver.notifications.repository.NotificationRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Skeleton only. TODO (next phase): RabbitMQ @RabbitListener on
 * RabbitMQConfig.QUEUE_NOTIFICATIONS, push over STOMP to the recipient,
 * mark-as-read endpoint.
 */
@Service
class NotificationService(
	private val notificationRepository: NotificationRepository
) {

	fun listForAccount(accountId: UUID): List<Notification> =
		notificationRepository.findByRecipientAccountId(accountId)
}

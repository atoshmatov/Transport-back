package uz.safecity.transportobserver.notifications.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

enum class NotificationType { INCIDENT, RAILSAFE_ALERT, SYSTEM, ACCOUNT }

/**
 * Placeholder — delivered to clients over STOMP (/user/queue/notifications,
 * see common.config.WebSocketConfig) and persisted here for history/read
 * state. Produced by consuming RabbitMQ events from other modules
 * (see common.config.RabbitMQConfig#QUEUE_NOTIFICATIONS).
 */
@Entity
@Table(name = "notifications")
class Notification(

	@Column(name = "recipient_account_id", nullable = false)
	var recipientAccountId: UUID,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var type: NotificationType,

	@Column(nullable = false)
	var title: String,

	@Column(columnDefinition = "text")
	var body: String? = null,

	@Column(name = "related_entity_id")
	var relatedEntityId: UUID? = null,

	@Column(name = "is_read", nullable = false)
	var isRead: Boolean = false

) : BaseEntity()

package uz.safecity.transportobserver.notifications.repository

import uz.safecity.transportobserver.notifications.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
	fun findByRecipientAccountId(recipientAccountId: UUID): List<Notification>
}

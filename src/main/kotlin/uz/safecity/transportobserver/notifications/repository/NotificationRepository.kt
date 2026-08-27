package uz.safecity.transportobserver.notifications.repository

import uz.safecity.transportobserver.notifications.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {

	fun findByRecipientAccountIdOrderByCreatedAtDesc(recipientAccountId: UUID): List<Notification>

	/**
	 * Ownership-scoped single-row lookup for `PATCH /{id}/read` — same "scoping baked into the
	 * query itself" pattern as [uz.safecity.transportobserver.incidents.repository.IncidentRepository.findByIdAndAssignedInspectorId]:
	 * a foreign notification id simply doesn't match (empty [Optional]), so the service/controller
	 * turns that into a 404, never a 403 that would confirm the row exists to someone probing ids.
	 */
	fun findByIdAndRecipientAccountId(id: UUID, recipientAccountId: UUID): Optional<Notification>

	/** Badge count backing `GET /unread-count`. */
	fun countByRecipientAccountIdAndIsReadFalse(recipientAccountId: UUID): Long

	/**
	 * Bulk `PATCH /read-all` — a single `UPDATE` rather than loading every unread row into memory
	 * and saving them back one by one, same rationale as the atomic-update methods on
	 * [uz.safecity.transportobserver.auth.repository.AccountRepository] (failed-attempts counter,
	 * `lastActiveAt`, ...). Returns the number of rows actually flipped.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.isRead = true where n.recipientAccountId = :recipientAccountId and n.isRead = false")
	fun markAllAsRead(@Param("recipientAccountId") recipientAccountId: UUID): Int
}

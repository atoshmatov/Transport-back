package uz.safecity.transportobserver.notifications.service

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.notifications.entity.Notification
import uz.safecity.transportobserver.notifications.entity.NotificationType
import uz.safecity.transportobserver.notifications.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read/write side of the `notifications` module. Delivery is currently direct
 * service-to-service (e.g. [uz.safecity.transportobserver.incidents.service.IncidentService]
 * calls [notifyAssignment]/[notifySosToAdmins] straight after the DB write that triggers it)
 * rather than via a RabbitMQ consumer on [uz.safecity.transportobserver.common.config.RabbitMQConfig.QUEUE_NOTIFICATIONS] —
 * that queue/consumer wiring is a bigger, separate piece of work (real async decoupling between
 * modules) than the immediate "create a Notification row when X happens" gap this closes. TODO
 * (next phase): move [notifyAssignment]/[notifySosToAdmins] callers to publish a RabbitMQ event
 * instead and add a `@RabbitListener` here that does the same [create] call.
 *
 * Every `me/...`-shaped method below is scoped to the caller's own `recipientAccountId`, same
 * "scoping baked into the query" rule as the rest of this codebase (see
 * [uz.safecity.transportobserver.incidents.service.IncidentService] kdoc) — a foreign
 * notification id 404s rather than 403ing, so it can't confirm to a caller that another
 * account's notification even exists.
 */
@Service
class NotificationService(
	private val notificationRepository: NotificationRepository,
	private val accountRepository: AccountRepository
) {

	fun listForAccount(accountId: UUID): List<Notification> =
		notificationRepository.findByRecipientAccountIdOrderByCreatedAtDesc(accountId)

	/** `GET /api/v1/notifications/unread-count` — badge count for the caller only. */
	fun unreadCountForAccount(accountId: UUID): Long =
		notificationRepository.countByRecipientAccountIdAndIsReadFalse(accountId)

	/** `PATCH /api/v1/notifications/{id}/read`. 404s on a foreign notification id — see class kdoc. */
	@Transactional
	fun markAsRead(id: UUID, accountId: UUID): Notification {
		val notification = notificationRepository.findByIdAndRecipientAccountId(id, accountId)
			.orElseThrow { ResourceNotFoundException("error.notification.not-found", id) }
		if (!notification.isRead) {
			notification.isRead = true
			return notificationRepository.save(notification)
		}
		return notification
	}

	/** `PATCH /api/v1/notifications/read-all`. Returns how many rows were actually flipped. */
	@Transactional
	fun markAllAsRead(accountId: UUID): Int = notificationRepository.markAllAsRead(accountId)

	/**
	 * Shared low-level creator — used both by the controller-facing flows above's future callers
	 * and directly by [notifyAssignment]/[notifySosToAdmins] below.
	 */
	@Transactional
	fun create(recipientAccountId: UUID, type: NotificationType, title: String, body: String? = null, relatedEntityId: UUID? = null): Notification =
		notificationRepository.save(
			Notification(
				recipientAccountId = recipientAccountId,
				type = type,
				title = title,
				body = body,
				relatedEntityId = relatedEntityId
			)
		)

	/**
	 * Called from [uz.safecity.transportobserver.incidents.service.IncidentService.assignInspector]
	 * (and the assign-on-create path in [uz.safecity.transportobserver.incidents.service.IncidentService.create])
	 * right after an inspector is assigned to an [uz.safecity.transportobserver.incidents.entity.Incident] —
	 * NOT called for an INSPECTOR's own self-assign-on-create, since notifying someone that they
	 * assigned themselves is pointless noise.
	 */
	@Transactional
	fun notifyAssignment(inspectorAccountId: UUID, incidentId: UUID, incidentTitle: String) {
		create(
			recipientAccountId = inspectorAccountId,
			type = NotificationType.INCIDENT,
			title = "Sizga yangi topshiriq biriktirildi",
			body = incidentTitle,
			relatedEntityId = incidentId
		)
	}

	/**
	 * Called from [uz.safecity.transportobserver.incidents.service.IncidentService.createSos] right
	 * after an SOS incident is created — broadcasts a [NotificationType.SOS] row to every active
	 * SUPER_ADMIN/ADMIN/OPERATOR account (the same "dispatch-tier" role set as
	 * [uz.safecity.transportobserver.incidents.service.IncidentService]'s `assertCanAssignInspector`),
	 * not just ADMIN/OPERATOR literally, since a favqulodda (emergency) signal should never be
	 * missed by whichever of the three roles happens to be on shift.
	 */
	@Transactional
	fun notifySosToAdmins(inspectorName: String?, incidentId: UUID) {
		val recipients = DISPATCH_ROLES.flatMap { accountRepository.findByRoleAndIsActive(it, true) }
		val title = "Favqulodda holat: SOS signal"
		val body = if (inspectorName != null) "$inspectorName SOS signal yubordi" else "Inspektor SOS signal yubordi"
		recipients.forEach { account ->
			create(
				recipientAccountId = requireNotNull(account.id),
				type = NotificationType.SOS,
				title = title,
				body = body,
				relatedEntityId = incidentId
			)
		}
	}

	companion object {
		private val DISPATCH_ROLES = setOf(RoleType.SUPER_ADMIN, RoleType.ADMIN, RoleType.OPERATOR)
	}
}

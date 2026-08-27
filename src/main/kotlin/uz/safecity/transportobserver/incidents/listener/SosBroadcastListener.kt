package uz.safecity.transportobserver.incidents.listener

import uz.safecity.transportobserver.incidents.event.SosCreatedEvent
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Pushes the [uz.safecity.transportobserver.incidents.dto.SosBroadcastDto] over STOMP to
 * [SOS_TOPIC] only AFTER the `@Transactional` [uz.safecity.transportobserver.incidents.service.IncidentService.createSos]
 * call that published the [SosCreatedEvent] has actually committed.
 *
 * Prior to this, `simpMessagingTemplate.convertAndSend(...)` was called directly inside that
 * `@Transactional` method, BEFORE the surrounding transaction committed. If anything after that
 * point in the same transaction (or the commit itself) failed and rolled back, an admin/operator
 * dashboard already connected to `/topic/sos` would have received a live SOS push for an incident
 * that was never actually persisted — a push/DB inconsistency for exactly the one signal
 * ("favqulodda holat") that most needs to be trustworthy. Routing the push through
 * [TransactionalEventListener] with [TransactionPhase.AFTER_COMMIT] closes that gap: if the
 * transaction rolls back, this listener simply never runs, and the push and the DB row can never
 * disagree.
 *
 * Note for tests: Spring's default `@Transactional` test rollback means AFTER_COMMIT listeners do
 * NOT fire in a plain `@SpringBootTest @Transactional` test (the test transaction never commits) —
 * see [uz.safecity.transportobserver.incidents.service.IncidentSosServiceTests] kdoc for how that
 * class avoids depending on this listener firing.
 */
@Component
class SosBroadcastListener(
	private val simpMessagingTemplate: SimpMessagingTemplate
) {

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	fun onSosCreated(event: SosCreatedEvent) {
		simpMessagingTemplate.convertAndSend(SOS_TOPIC, event.broadcast)
	}

	companion object {
		/** STOMP destination for the real-time admin/operator SOS broadcast. */
		private const val SOS_TOPIC = "/topic/sos"
	}
}

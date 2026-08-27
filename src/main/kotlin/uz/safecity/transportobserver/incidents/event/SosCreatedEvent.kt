package uz.safecity.transportobserver.incidents.event

import uz.safecity.transportobserver.incidents.dto.SosBroadcastDto

/**
 * Published by [uz.safecity.transportobserver.incidents.service.IncidentService.createSos] right
 * after the SOS [uz.safecity.transportobserver.incidents.entity.Incident] row is saved (and the
 * DB-backed [uz.safecity.transportobserver.notifications.service.NotificationService.notifySosToAdmins]
 * rows are written) — still inside that method's `@Transactional` scope.
 *
 * Deliberately NOT turned into a synchronous STOMP push right there. See
 * [uz.safecity.transportobserver.incidents.listener.SosBroadcastListener] kdoc for why the actual
 * `/topic/sos` send is deferred to AFTER_COMMIT instead of happening inside the transaction.
 */
data class SosCreatedEvent(val broadcast: SosBroadcastDto)

package uz.safecity.transportobserver.incidents.dto

import java.time.Instant
import java.util.UUID

/**
 * `POST /api/v1/inspector/me/sos` request body — see
 * [uz.safecity.transportobserver.incidents.service.IncidentService.createSos] kdoc. Both fields
 * optional TOGETHER (same "GPS o'chirilgan holat" rule as [CreateIncidentRequest]) — an inspector
 * in danger must still be able to send SOS with no GPS fix at all; a half-supplied pair is still
 * rejected as a client bug by [uz.safecity.transportobserver.common.util.GeoUtils.toPointOrNull].
 */
data class CreateSosRequest(
	val latitude: Double? = null,
	val longitude: Double? = null
)

/**
 * Broadcast over STOMP to `/topic/sos` the instant an SOS incident is created — see
 * [uz.safecity.transportobserver.incidents.service.IncidentService.createSos] kdoc. Deliberately a
 * separate, smaller shape than [IncidentDto] (no `evidenceCount`/`clientUuid`/... noise) since this
 * is a live "drop everything" alert for the admin/operator dashboard, not a full incident record —
 * the dashboard can always `GET /incidents/{id}` for the rest once it reacts to this.
 */
data class SosBroadcastDto(
	val incidentId: UUID,
	val inspectorAccountId: UUID,
	val inspectorName: String?,
	val latitude: Double?,
	val longitude: Double?,
	val createdAt: Instant
)

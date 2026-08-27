package uz.safecity.transportobserver.incidents.dto

import uz.safecity.transportobserver.incidents.entity.IncidentType
import java.time.Instant
import java.util.UUID

/**
 * `POST /api/v1/inspector/me/sos` request body — see
 * [uz.safecity.transportobserver.incidents.service.IncidentService.createSos] kdoc. [latitude]/
 * [longitude] optional TOGETHER (same "GPS o'chirilgan holat" rule as [CreateIncidentRequest]) —
 * an inspector in danger must still be able to send SOS with no GPS fix at all; a half-supplied
 * pair is still rejected as a client bug by
 * [uz.safecity.transportobserver.common.util.GeoUtils.toPointOrNull].
 *
 * [type] is the mobile SOS screen's "Holat turi" picker (Yo'l-transport hodisasi / Yong'in /
 * Texnik nosozlik / Tibbiy yordam -> [IncidentType.ACCIDENT] / [IncidentType.FIRE] /
 * [IncidentType.TECHNICAL_FAULT] / [IncidentType.MEDICAL]). Defaults to [IncidentType.OTHER]
 * rather than being `@NotNull`/required: an older mobile build that predates this picker doesn't
 * send it at all, and an SOS request must never be rejected for a missing field — the whole point
 * of the panic button is that it always goes through (same "don't block on a field the client
 * doesn't have yet" reasoning as [CreateIncidentRequest.actionType]).
 *
 * [clientUuid] mirrors [CreateIncidentRequest.clientUuid] — the mobile SOS screen retries the
 * request on a network error (1s/2s/4s backoff) since a lost SOS signal is more dangerous than a
 * duplicate one, but that means a request that actually reached the server and succeeded, only to
 * have its response lost to the same timeout, would otherwise create a second Incident on retry.
 * Optional (an older mobile build that predates this field must never be rejected — same
 * "never block the panic button on a missing field" reasoning as [type] above): when absent, no
 * dedup is attempted and every call creates a new Incident, exactly as before this field existed.
 * See [uz.safecity.transportobserver.incidents.service.IncidentService.createSos] kdoc for the
 * dedup logic itself.
 */
data class CreateSosRequest(
	val latitude: Double? = null,
	val longitude: Double? = null,
	val type: IncidentType = IncidentType.OTHER,
	val clientUuid: UUID? = null
)

/**
 * Broadcast over STOMP to `/topic/sos` the instant an SOS incident is created — see
 * [uz.safecity.transportobserver.incidents.service.IncidentService.createSos] kdoc. Deliberately a
 * separate, smaller shape than [IncidentDto] (no `evidenceCount`/`clientUuid`/... noise) since this
 * is a live "drop everything" alert for the admin/operator dashboard, not a full incident record —
 * the dashboard can always `GET /incidents/{id}` for the rest once it reacts to this.
 *
 * [incidentType] is included (unlike most of [IncidentDto]'s other fields) because it lets the
 * live dashboard alert show/icon the right kind of emergency (yong'in vs tibbiy vs ...) the
 * instant it arrives, without waiting on the `GET /incidents/{id}` follow-up.
 */
data class SosBroadcastDto(
	val incidentId: UUID,
	val inspectorAccountId: UUID,
	val inspectorName: String?,
	val latitude: Double?,
	val longitude: Double?,
	val incidentType: IncidentType,
	val createdAt: Instant
)

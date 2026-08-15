package uz.safecity.transportobserver.incidents.dto

import uz.safecity.transportobserver.incidents.entity.ActionType
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class IncidentDto(
	val id: UUID,
	val title: String,
	val description: String?,
	val type: IncidentType,
	val status: IncidentStatus,
	/** What the inspector did — see [ActionType] kdoc; null on incidents created before this field existed. */
	val actionType: ActionType?,
	val latitude: Double?,
	val longitude: Double?,
	val occurredAt: Instant?,
	val assignedInspectorId: UUID?,
	// TODO (region module): text field until a real `regions` table exists — see Incident kdoc.
	val regionName: String?,
	/** Client-generated offline-dedup key, if this incident came from the mobile app — see Incident kdoc. */
	val clientUuid: UUID?,
	/**
	 * Number of [uz.safecity.transportobserver.incidents.entity.Evidence] rows attached —
	 * NOT the evidence items themselves (those carry short-lived presigned URLs, fetched
	 * separately via `GET /incidents/{id}/evidence` so a list-of-incidents response doesn't
	 * mint N presigned URLs nobody asked to see yet — see IncidentService kdoc).
	 */
	val evidenceCount: Long,
	/** Last time this record (incl. its location/status) changed — see BaseEntity.updatedAt. */
	val updatedAt: Instant?
) {
	companion object {
		fun from(incident: Incident, evidenceCount: Long = 0) = IncidentDto(
			id = requireNotNull(incident.id),
			title = incident.title,
			description = incident.description,
			type = incident.type,
			status = incident.status,
			actionType = incident.actionType,
			latitude = incident.location?.y,
			longitude = incident.location?.x,
			occurredAt = incident.occurredAt,
			assignedInspectorId = incident.assignedInspectorId,
			regionName = incident.regionName,
			clientUuid = incident.clientUuid,
			evidenceCount = evidenceCount,
			updatedAt = incident.updatedAt
		)
	}
}

/**
 * Mobile's primary write path (TZ sections 7/8) — see IncidentService#create kdoc for the
 * clientUuid dedup + auto-assign rules. [latitude]/[longitude] are optional TOGETHER (TZ 8.3:
 * "GPS o'chirilgan holat" — a report must still go through with no GPS) but a half-supplied
 * pair (only one of the two) is rejected by IncidentService/GeoUtils as a client bug, not
 * silently dropped.
 *
 * [actionType] is optional, not `@NotNull`: older/not-yet-updated mobile app builds don't send
 * it at all, and a report must still go through without it (same "must not block on a field the
 * client doesn't have yet" reasoning as [latitude]/[longitude] being independently optional).
 * Once the mobile client is fully migrated to send it on every report, product may decide to
 * tighten this to required — that is a client-rollout decision, not a backend one.
 */
data class CreateIncidentRequest(
	@field:NotBlank(message = "title majburiy")
	@field:Size(max = 255, message = "title 255 belgidan oshmasligi kerak")
	val title: String,

	val description: String? = null,

	@field:NotNull(message = "type majburiy")
	val type: IncidentType?,

	/** What the inspector did while filing this report — see [ActionType] kdoc. */
	val actionType: ActionType? = null,

	val latitude: Double? = null,
	val longitude: Double? = null,

	/** Defaults to server time (now) when omitted — see IncidentService#create. */
	val occurredAt: Instant? = null,

	/** Client-generated UUID for offline dedup — see Incident.clientUuid kdoc. */
	val clientUuid: UUID? = null
)

/** SUPER_ADMIN/ADMIN/OPERATOR only — see IncidentController#assignInspector. */
data class AssignInspectorRequest(
	@field:NotNull(message = "inspectorAccountId majburiy")
	val inspectorAccountId: UUID?
)

/**
 * SUPER_ADMIN/ADMIN/OPERATOR may set any incident's status; INSPECTOR may set it only on an
 * incident assigned to itself — see IncidentController#updateStatus / IncidentService#updateStatus.
 * No status-transition graph is enforced yet (e.g. RESOLVED -> NEW is currently allowed) — TODO
 * once the TZ defines the allowed transitions, validate them here or in the service.
 */
data class UpdateIncidentStatusRequest(
	@field:NotNull(message = "status majburiy")
	val status: IncidentStatus?
)

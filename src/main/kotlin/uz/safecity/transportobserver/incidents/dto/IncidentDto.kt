package uz.safecity.transportobserver.incidents.dto

import uz.safecity.transportobserver.incidents.entity.ActionType
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
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
	/**
	 * [uz.safecity.transportobserver.employees.entity.Employee.fullName] of the account in
	 * [assignedInspectorId], resolved via `Account.employeeId` (see IncidentService kdoc for why
	 * that hop is needed — the incident stores the *account* id, not the employee id). Null when
	 * unassigned, or (rare) when the assigned account has no linked Employee row. This exists so
	 * the web admin board can show a name instead of a bare UUID next to "tayinlangan" — the raw
	 * id alone was the gap that made assignment invisible/unusable from the web UI.
	 */
	val assignedInspectorName: String?,
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
	val updatedAt: Instant?,
	/** See [Incident.isSos] kdoc — true only for incidents created via `POST /inspector/me/sos`. */
	val isSos: Boolean
) {
	companion object {
		fun from(incident: Incident, evidenceCount: Long = 0, assignedInspectorName: String? = null) = IncidentDto(
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
			assignedInspectorName = assignedInspectorName,
			regionName = incident.regionName,
			clientUuid = incident.clientUuid,
			evidenceCount = evidenceCount,
			updatedAt = incident.updatedAt,
			isSos = incident.isSos
		)
	}
}

/** One row of the "Hodisa kartasi" status-history timeline — see [uz.safecity.transportobserver.incidents.entity.IncidentStatusEvent] kdoc. */
data class IncidentStatusEventDto(
	val label: String,
	val status: IncidentStatus?,
	val actorAccountId: UUID?,
	/** [uz.safecity.transportobserver.employees.entity.Employee.fullName] of [actorAccountId], if resolvable — null for system-triggered steps or accounts with no linked Employee. */
	val actorName: String?,
	val occurredAt: Instant
)

/**
 * `GET /api/v1/incidents/{id}` response — a superset of [IncidentDto] with the extra detail the
 * mobile "Hodisa kartasi" (`TO-Screen.dc.html` incidentDetail) screen needs but a list row doesn't:
 * vehicle/passenger info, the "ko'rilgan chora" (resolution) snapshot, and the full status-history
 * timeline. Kept as a separate DTO (not folded into [IncidentDto] itself) so `GET /incidents`
 * (list/board) doesn't pay for a per-row vehicle lookup + status-history query it never displays —
 * see IncidentService#list kdoc for the batching this would otherwise have to duplicate.
 */
data class IncidentDetailDto(
	val id: UUID,
	val title: String,
	val description: String?,
	val type: IncidentType,
	val status: IncidentStatus,
	val actionType: ActionType?,
	val latitude: Double?,
	val longitude: Double?,
	val occurredAt: Instant?,
	val assignedInspectorId: UUID?,
	val assignedInspectorName: String?,
	val regionName: String?,
	val clientUuid: UUID?,
	val evidenceCount: Long,
	val updatedAt: Instant?,
	val isSos: Boolean,
	/** See [uz.safecity.transportobserver.incidents.entity.Incident.vehicleId] kdoc. */
	val vehicleId: UUID?,
	/** [uz.safecity.transportobserver.vehicles.entity.Vehicle.plateNumber], resolved via [vehicleId] — null when unset or the vehicle no longer resolves. */
	val vehiclePlateNumber: String?,
	/** [uz.safecity.transportobserver.vehicles.entity.Vehicle.model], resolved via [vehicleId]. */
	val vehicleModel: String?,
	/** See [uz.safecity.transportobserver.incidents.entity.Incident.passengerCount] kdoc. */
	val passengerCount: Int?,
	/** "Ko'rilgan chora" snapshot — see [uz.safecity.transportobserver.incidents.entity.Incident.resolutionNote] kdoc. */
	val resolutionNote: String?,
	val fineAmount: String?,
	val resolutionDeadline: LocalDate?,
	val resolutionResponsibleAccountId: UUID?,
	/** [uz.safecity.transportobserver.employees.entity.Employee.fullName] of [resolutionResponsibleAccountId], if resolvable. */
	val resolutionResponsibleName: String?,
	/** Vaqt bo'yicha (eskidan yangiga) tartiblangan — see [uz.safecity.transportobserver.incidents.entity.IncidentStatusEvent]. */
	val statusHistory: List<IncidentStatusEventDto>
) {
	companion object {
		fun from(
			incident: Incident,
			evidenceCount: Long,
			assignedInspectorName: String?,
			vehiclePlateNumber: String?,
			vehicleModel: String?,
			resolutionResponsibleName: String?,
			statusHistory: List<IncidentStatusEventDto>
		) = IncidentDetailDto(
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
			assignedInspectorName = assignedInspectorName,
			regionName = incident.regionName,
			clientUuid = incident.clientUuid,
			evidenceCount = evidenceCount,
			updatedAt = incident.updatedAt,
			isSos = incident.isSos,
			vehicleId = incident.vehicleId,
			vehiclePlateNumber = vehiclePlateNumber,
			vehicleModel = vehicleModel,
			passengerCount = incident.passengerCount,
			resolutionNote = incident.resolutionNote,
			fineAmount = incident.fineAmount,
			resolutionDeadline = incident.resolutionDeadline,
			resolutionResponsibleAccountId = incident.resolutionResponsibleAccountId,
			resolutionResponsibleName = resolutionResponsibleName,
			statusHistory = statusHistory
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
 *
 * [assignedInspectorId] lets SUPER_ADMIN/ADMIN/OPERATOR assign an inspector in the same call
 * that creates the incident (the web "hodisa yaratish" form's one-step assign-on-create path) —
 * see IncidentService#create kdoc. It is the INSPECTOR's *account* id (same as
 * [AssignInspectorRequest.inspectorAccountId] on the separate `PATCH /{id}/assign` endpoint),
 * not the Employee id — a frequent source of confusion, since the web employee roster's "id"
 * field is usually the Employee id. Ignored entirely for an INSPECTOR caller, who always
 * auto-assigns to themselves regardless of what (if anything) is sent here.
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
	val clientUuid: UUID? = null,

	/** SUPER_ADMIN/ADMIN/OPERATOR-only assign-on-create — see this class's kdoc above. */
	val assignedInspectorId: UUID? = null,

	/**
	 * Transport vehicle involved, if identifiable on the mobile `ReportIncidentScreen` — see
	 * [uz.safecity.transportobserver.incidents.entity.Incident.vehicleId] kdoc. Optional: not
	 * every report involves an identifiable vehicle.
	 */
	val vehicleId: UUID? = null,

	/**
	 * Passenger count observed at report time — see
	 * [uz.safecity.transportobserver.incidents.entity.Incident.passengerCount] kdoc. Optional for
	 * the same reason as [vehicleId].
	 */
	val passengerCount: Int? = null
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

/**
 * `PATCH /api/v1/incidents/{id}/resolution` — SUPER_ADMIN/ADMIN/OPERATOR only (see
 * IncidentController#updateResolution / IncidentService#updateResolution kdoc). All fields are
 * optional and this is a full REPLACE of the resolution snapshot per call (not a partial merge,
 * matches [UpdateIncidentStatusRequest]/[uz.safecity.transportobserver.vehicles.dto.UpdateVehicleRequest]
 * full-replace convention already used elsewhere in this codebase) — an omitted/null field clears
 * whatever was previously set, so the web/mobile client is expected to resend the full snapshot
 * (echoing back unchanged fields) rather than sending only the one field it wants to change.
 */
data class UpdateIncidentResolutionRequest(
	/** "Bayonnoma tuzildi" style free text — see [uz.safecity.transportobserver.incidents.entity.Incident.resolutionNote] kdoc. */
	val resolutionNote: String? = null,
	/** e.g. "1 BHM" — see [uz.safecity.transportobserver.incidents.entity.Incident.fineAmount] kdoc for why this is free text, not [java.math.BigDecimal]. */
	val fineAmount: String? = null,
	val resolutionDeadline: LocalDate? = null,
	/** [uz.safecity.transportobserver.auth.entity.Account.id] — validated to exist by IncidentService#updateResolution. */
	val resolutionResponsibleAccountId: UUID? = null
)

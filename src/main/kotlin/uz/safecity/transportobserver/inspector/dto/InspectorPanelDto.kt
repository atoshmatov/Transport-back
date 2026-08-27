package uz.safecity.transportobserver.inspector.dto

import com.fasterxml.jackson.annotation.JsonInclude
import uz.safecity.transportobserver.incidents.entity.ActionType
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.vehicles.dto.VehicleDto
import uz.safecity.transportobserver.vehicles.entity.VehicleOwnerType
import uz.safecity.transportobserver.vehicles.entity.VehicleType
import java.time.Instant
import java.util.UUID

/**
 * Response for `GET /api/v1/inspector/dashboard/summary`. Field names are
 * fixed by the Inspector web-panel frontend's TypeScript type — do not rename
 * without updating the frontend contract at the same time.
 *
 * [openInspectionsCount]/[completedTodayCount]/[recentInspections] are backed
 * by the real `inspections` module ([Inspection]) — see
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService] kdoc
 * for why this replaced the earlier `Incident`-based placeholder.
 *
 * [activeCheckpointsCount] is a real, system-wide count of active checkpoints
 * (`CheckpointRepository.countByIsActiveTrue`) — see
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService] kdoc
 * for why it isn't scoped to "this inspector's" checkpoints yet.
 */
data class DashboardSummaryDto(
	val openInspectionsCount: Int,
	val completedTodayCount: Int,
	val activeCheckpointsCount: Int,
	val lastUpdatedAt: Instant,
	val recentInspections: List<RecentInspectionDto>
)

/**
 * [checkpointName] is the real [uz.safecity.transportobserver.checkpoints.entity.Checkpoint.name]
 * for [Inspection.checkpointId], resolved via a batched lookup in
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService.getDashboardSummary]
 * (same N+1-avoidance pattern as [uz.safecity.transportobserver.map.dto.VehicleLocationDto]).
 * Falls back to a fixed placeholder string (never `null`) in the — normally
 * impossible, since checkpoints are never hard-deleted — case where the id
 * doesn't resolve, because the frontend type requires a non-null string here
 * (unlike [InspectorCurrentLocationDto.checkpointName], which is nullable).
 */
data class RecentInspectionDto(
	val id: UUID,
	val checkpointName: String,
	val status: String,
	val performedAt: Instant
) {
	companion object {
		fun from(inspection: Inspection, checkpointName: String?) = RecentInspectionDto(
			id = requireNotNull(inspection.id),
			checkpointName = checkpointName ?: "Noma'lum nazorat punkti",
			status = inspection.status.name,
			performedAt = inspection.performedAt ?: inspection.updatedAt ?: inspection.scheduledAt ?: Instant.now()
		)
	}
}

/**
 * Response for `GET /api/v1/inspector/map/current-location`. [checkpointName],
 * [lat] and [lng] are always `null` for now — the `checkpoints` module exists,
 * but there is still no Inspector-Checkpoint assignment mechanism to source a
 * real "this inspector's current checkpoint" from (see InspectorPanelService
 * kdoc). The frontend (`MapView.vue`) already handles this via `v-if`, per
 * the TASK description.
 *
 * `@JsonInclude(ALWAYS)` overrides the app-wide `non_null` Jackson default
 * (see application.yml `spring.jackson.default-property-inclusion`) so these
 * nullable fields are always serialized as explicit JSON `null` rather than
 * omitted — the frontend contract declares them as `T | null`, not optional,
 * so the key must always be present in the payload.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class InspectorCurrentLocationDto(
	val checkpointName: String?,
	val lat: Double?,
	val lng: Double?,
	val lastUpdatedAt: Instant
)

/**
 * `GET /api/v1/inspector/me/stats` — the 5 headline numbers on the mobile Profile screen. Fixes
 * the hardcoded "4.8" / "#12" issue this task closes for the leaderboard side (see
 * [uz.safecity.transportobserver.ratings.controller.RatingController]); this DTO covers the
 * separate "5 asosiy metrik" block.
 *
 * [totalInspectionsCount] deliberately reuses the SAME metric as the leaderboard
 * ([uz.safecity.transportobserver.ratings.dto.MyRatingDto.completedInspectionsCount] — COMPLETED
 * [Inspection]s assigned to this inspector), not a count of [Incident] reports: "tekshiruv"
 * (inspection) is this codebase's established term for the `inspections` module specifically
 * (see [uz.safecity.transportobserver.ratings.service.RatingService] kdoc, "eng ko'p tekshiruv
 * bajargan inspektorlar"). The other 4 fields are [ActionType] counts on this inspector's own
 * [Incident] reports — a deliberately different data source, since Incident ("what I observed
 * and reported in the field") and Inspection ("what I was assigned to check") are different
 * concepts in this codebase (see [Incident]/[Inspection] kdoc) and this screen surfaces both.
 */
data class InspectorStatsDto(
	val totalInspectionsCount: Int,
	val violationsRecordedCount: Int,
	val warningsGivenCount: Int,
	val photoEvidenceSubmittedCount: Int,
	val dangersReportedCount: Int
)

/**
 * `GET /api/v1/inspector/me/incident-type-breakdown` row — mobile Profile screen "hodisa turi
 * bo'yicha" donut chart. Always one row per [IncidentType] value (even `cnt = 0`), so the client
 * can render a stable legend without special-casing missing categories.
 */
data class IncidentTypeCountDto(
	val type: IncidentType,
	val count: Long
)

/**
 * `GET /api/v1/inspector/me/action-type-breakdown` row — mobile Profile screen "harakat turi
 * bo'yicha" donut chart. One row per [ActionType] value (even `cnt = 0`); incidents with a null
 * `actionType` (created before this field existed — see [Incident] kdoc) are excluded rather
 * than folded into a fabricated "unspecified" bucket.
 */
data class ActionTypeCountDto(
	val actionType: ActionType,
	val count: Long
)

/**
 * `GET /api/v1/inspector/me/recent-activity` row — mobile Profile screen "so'nggi ishlar" list.
 * Backed by [Incident] (not [Inspection]) since it needs both [IncidentType] and [ActionType],
 * the same two fields the donut breakdowns above chart — see [InspectorStatsDto] kdoc for why
 * these are different data sources.
 */
data class RecentActivityDto(
	val id: UUID,
	val title: String,
	val type: IncidentType,
	val actionType: ActionType?,
	val status: IncidentStatus,
	val occurredAt: Instant?,
	val createdAt: Instant
) {
	companion object {
		fun from(incident: Incident) = RecentActivityDto(
			id = requireNotNull(incident.id),
			title = incident.title,
			type = incident.type,
			actionType = incident.actionType,
			status = incident.status,
			occurredAt = incident.occurredAt,
			createdAt = requireNotNull(incident.createdAt)
		)
	}
}

/**
 * `GET /api/v1/inspector/vehicles/{id}` — mobile "Transport vositasi" (vehicleDetail) screen
 * (`TO-Screen.dc.html`, ~L918-945), opened from the vehicle line on the mobile "Hodisa kartasi"
 * (incidentDetail) screen or from a recent-incident row on the Home screen.
 *
 * Deliberately NOT every field the design mockup shows: VIN, owner organization/STIR, route
 * permit, technical-inspection dates etc. are all admin-entered master data that does not exist
 * on [uz.safecity.transportobserver.vehicles.entity.Vehicle] yet, and surfacing them would require
 * a new admin-panel (web frontend) form to capture them — out of scope for this backend-only task.
 * This DTO surfaces only what is real today: the vehicle's existing registry fields (same subset
 * [VehicleDto] already exposes) plus [violationHistory], which is now genuinely computable because
 * [uz.safecity.transportobserver.incidents.entity.Incident.vehicleId] exists (see that field's kdoc)
 * — unlike the still-missing master-data fields above, this is not a placeholder.
 */
data class VehicleDetailDto(
	val id: UUID,
	val plateNumber: String,
	val model: String?,
	val type: VehicleType,
	val regionName: String?,
	val ownerType: VehicleOwnerType?,
	val violationHistory: List<VehicleViolationDto>
) {
	companion object {
		fun from(vehicle: VehicleDto, violationHistory: List<VehicleViolationDto>) = VehicleDetailDto(
			id = vehicle.id,
			plateNumber = vehicle.plateNumber,
			model = vehicle.model,
			type = vehicle.type,
			regionName = vehicle.regionName,
			ownerType = vehicle.ownerType,
			violationHistory = violationHistory
		)
	}
}

/**
 * One row of [VehicleDetailDto.violationHistory] — an [Incident] this vehicle was involved in via
 * [Incident.vehicleId], regardless of which inspector filed it or is currently assigned to it:
 * this is the *vehicle's* own record, not "my" incidents (contrast [RecentActivityDto], which IS
 * scoped to the calling inspector).
 *
 * [description] is truncated to [DESCRIPTION_MAX_LENGTH] — this is a summary row on a history
 * list, not the full "Hodisa kartasi" detail view (`GET /incidents/{id}` already exists for that,
 * via [incidentId]), so a very long free-text description shouldn't bloat this list response.
 *
 * [occurredAt] falls back to [Incident.createdAt] when [Incident.occurredAt] is null — same
 * "occurredAt is caller-reported and can be absent, createdAt always exists" reasoning as
 * [IncidentRepository.countDailyCreatedBetween]'s kdoc. In practice every [Incident] created
 * through [uz.safecity.transportobserver.incidents.service.IncidentService] already has
 * `occurredAt` set (defaults to server time when the client omits it), so this fallback only
 * matters for the theoretical case of a row inserted outside that service.
 */
data class VehicleViolationDto(
	val incidentId: UUID,
	val type: IncidentType,
	val description: String?,
	val occurredAt: Instant,
	val status: IncidentStatus
) {
	companion object {
		private const val DESCRIPTION_MAX_LENGTH = 200

		fun from(incident: Incident) = VehicleViolationDto(
			incidentId = requireNotNull(incident.id),
			type = incident.type,
			description = incident.description?.take(DESCRIPTION_MAX_LENGTH),
			occurredAt = incident.occurredAt ?: requireNotNull(incident.createdAt),
			status = incident.status
		)
	}
}

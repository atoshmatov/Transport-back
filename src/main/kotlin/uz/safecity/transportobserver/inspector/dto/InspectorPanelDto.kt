package uz.safecity.transportobserver.inspector.dto

import com.fasterxml.jackson.annotation.JsonInclude
import uz.safecity.transportobserver.inspections.entity.Inspection
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

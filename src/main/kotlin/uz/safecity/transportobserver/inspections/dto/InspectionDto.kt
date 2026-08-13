package uz.safecity.transportobserver.inspections.dto

import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/**
 * [checkpointName] is enriched from the [Checkpoint] registry by matching
 * [checkpointId] against [Checkpoint.id] — see
 * [uz.safecity.transportobserver.inspections.service.InspectionService]'s
 * batched lookup (same N+1-avoidance pattern as
 * [uz.safecity.transportobserver.map.dto.VehicleLocationDto] / MapService).
 * Nullable: an [Inspection] whose `checkpointId` no longer matches any
 * registry row (should not normally happen, checkpoints are never hard
 * deleted — see Checkpoint kdoc) is still returned rather than dropped.
 */
data class InspectionDto(
	val id: UUID,
	val checkpointId: UUID,
	val checkpointName: String?,
	val assignedInspectorId: UUID?,
	val status: InspectionStatus,
	val scheduledAt: Instant?,
	val performedAt: Instant?,
	val notes: String?,
	val createdBy: UUID?,
	val createdAt: Instant?,
	val updatedAt: Instant?
) {
	companion object {
		fun from(inspection: Inspection, checkpoint: Checkpoint?) = InspectionDto(
			id = requireNotNull(inspection.id),
			checkpointId = inspection.checkpointId,
			checkpointName = checkpoint?.name,
			assignedInspectorId = inspection.assignedInspectorId,
			status = inspection.status,
			scheduledAt = inspection.scheduledAt,
			performedAt = inspection.performedAt,
			notes = inspection.notes,
			createdBy = inspection.createdBy,
			createdAt = inspection.createdAt,
			updatedAt = inspection.updatedAt
		)
	}
}

/** SUPER_ADMIN/ADMIN/OPERATOR only — see InspectionController#create. */
data class CreateInspectionRequest(
	@field:NotNull(message = "checkpointId majburiy")
	val checkpointId: UUID?,

	/** Optional at creation — an inspection can be planned before an inspector is assigned. */
	val assignedInspectorId: UUID? = null,

	val scheduledAt: Instant? = null,

	val notes: String? = null
)

/**
 * SUPER_ADMIN/ADMIN/OPERATOR may set any inspection's status; INSPECTOR may set it only on an
 * inspection assigned to itself — see InspectionController#updateStatus / InspectionService#updateStatus.
 * When [status] transitions to [InspectionStatus.COMPLETED] and [Inspection.performedAt] is not
 * already set, the service fills it in with the current time automatically.
 */
data class UpdateInspectionStatusRequest(
	@field:NotNull(message = "status majburiy")
	val status: InspectionStatus?,

	/** Optional outcome notes — typically filled in by the inspector when completing the task. */
	val notes: String? = null
)

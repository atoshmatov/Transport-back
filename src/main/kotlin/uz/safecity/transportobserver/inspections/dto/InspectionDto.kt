package uz.safecity.transportobserver.inspections.dto

import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import uz.safecity.transportobserver.inspections.entity.ChecklistResult
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionChecklistItem
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.entity.InspectionStatusEvent
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
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
 *
 * [checklistItems], [driverConfirmed] and [witnessName] are only ever consulted when [status] is
 * [InspectionStatus.COMPLETED] — see InspectionService#updateStatus kdoc for the "mobile submits
 * checklist+signatures together with the completion call" contract this shape backs. They are
 * silently ignored for any other target status rather than rejected, so a client that always
 * includes them (e.g. an empty [checklistItems] on a plain "boshlash" call) doesn't need special-casing.
 */
data class UpdateInspectionStatusRequest(
	@field:NotNull(message = "status majburiy")
	val status: InspectionStatus?,

	/** Optional outcome notes — typically filled in by the inspector when completing the task. */
	val notes: String? = null,

	/** "BANDLAR NATIJASI" — the full checklist, submitted wholesale. See class kdoc. */
	@field:Valid
	val checklistItems: List<ChecklistItemRequest>? = null,

	/** "TASDIQ VA IMZOLAR" -> haydovchi imzosi. See [Inspection.driverSignedAt] kdoc for what this timestamp does/doesn't mean. */
	val driverConfirmed: Boolean = false,

	/** "TASDIQ VA IMZOLAR" -> "Guvoh (ixtiyoriy)". */
	val witnessName: String? = null
)

/** One "BANDLAR NATIJASI" row submitted by the mobile client — see [InspectionChecklistItem] kdoc. */
data class ChecklistItemRequest(
	@field:NotBlank(message = "label majburiy")
	val label: String?,

	@field:NotNull(message = "result majburiy")
	val result: ChecklistResult?,

	/** "ANIQLANGAN KAMCHILIKLAR" tavsifi — conventionally set when [result] is [ChecklistResult.DEFICIENT], but not enforced here; the mobile UI is the one place that decision belongs. */
	val deficiencyNote: String? = null
)

/** One "BANDLAR NATIJASI" row in an API response. */
data class ChecklistItemDto(
	val id: UUID,
	val label: String,
	val result: ChecklistResult,
	val deficiencyNote: String?,
	val orderIndex: Int
) {
	companion object {
		fun from(item: InspectionChecklistItem) = ChecklistItemDto(
			id = requireNotNull(item.id),
			label = item.label,
			result = item.result,
			deficiencyNote = item.deficiencyNote,
			orderIndex = item.orderIndex
		)
	}
}

/** One "JARAYON" timeline row — see [InspectionStatusEvent] kdoc. */
data class InspectionStatusEventDto(
	val label: String,
	val occurredAt: Instant
) {
	companion object {
		fun from(event: InspectionStatusEvent) = InspectionStatusEventDto(
			label = event.label,
			occurredAt = event.occurredAt
		)
	}
}

/**
 * `GET /api/v1/inspections/{id}` response — a superset of [InspectionDto] with the "Tekshiruv
 * hisoboti" detail screen's extra sections (`TO-Screen.dc.html` `reportDetail`): "BANDLAR
 * NATIJASI" ([checklistItems]), "TASDIQ VA IMZOLAR" ([inspectorSignedAt]/[driverSignedAt]/
 * [witnessName]) and "JARAYON" ([statusHistory]). Mirrors
 * [uz.safecity.transportobserver.incidents.dto.IncidentDetailDto] exactly. The plain
 * [InspectionDto] (returned from list/create/updateStatus) intentionally stays a subset rather
 * than being widened to this shape — those call sites don't need the extra queries this pulls in.
 */
data class InspectionDetailDto(
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
	val updatedAt: Instant?,
	val inspectorSignedAt: Instant?,
	val driverSignedAt: Instant?,
	val witnessName: String?,
	/** Ordered per [InspectionChecklistItem.orderIndex] — see InspectionChecklistItemRepository#findByInspectionIdOrderByOrderIndexAsc. */
	val checklistItems: List<ChecklistItemDto>,
	/** Oldest-to-newest — see InspectionStatusEventRepository#findByInspectionIdOrderByOccurredAtAsc. */
	val statusHistory: List<InspectionStatusEventDto>
) {
	companion object {
		fun from(
			inspection: Inspection,
			checkpoint: Checkpoint?,
			checklistItems: List<InspectionChecklistItem>,
			statusHistory: List<InspectionStatusEvent>
		) = InspectionDetailDto(
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
			updatedAt = inspection.updatedAt,
			inspectorSignedAt = inspection.inspectorSignedAt,
			driverSignedAt = inspection.driverSignedAt,
			witnessName = inspection.witnessName,
			checklistItems = checklistItems.map { ChecklistItemDto.from(it) },
			statusHistory = statusHistory.map { InspectionStatusEventDto.from(it) }
		)
	}
}

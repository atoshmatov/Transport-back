package uz.safecity.transportobserver.shifts.dto

import uz.safecity.transportobserver.shifts.entity.WorkShift
import java.time.Instant
import java.util.UUID

/**
 * `POST /api/v1/inspector/me/shift/start`, `POST .../end`, and `GET .../current` response body.
 * [endedAt] is `null` while the shift is still open — see [WorkShift] kdoc. [checkpointId] is the
 * optional check-in checkpoint for this shift — see [WorkShift.checkpointId] kdoc.
 */
data class WorkShiftDto(
	val id: UUID,
	val inspectorId: UUID,
	val startedAt: Instant,
	val endedAt: Instant?,
	val checkpointId: UUID?
) {
	companion object {
		fun from(shift: WorkShift) = WorkShiftDto(
			id = requireNotNull(shift.id),
			inspectorId = shift.inspectorId,
			startedAt = shift.startedAt,
			endedAt = shift.endedAt,
			checkpointId = shift.checkpointId
		)
	}
}

/**
 * `POST /api/v1/inspector/me/shift/start` request body — optional (mirrors
 * [uz.safecity.transportobserver.incidents.dto.CreateSosRequest]'s "optional body" convention so
 * older mobile clients that don't send a body at all keep working unchanged). See
 * [WorkShift.checkpointId] kdoc for why this is optional rather than required.
 */
data class StartShiftRequest(
	val checkpointId: UUID? = null
)

/**
 * Lightweight "who has an open shift at checkpoint X right now" projection — backs
 * [uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService.getOnDuty].
 * Deliberately NOT the [WorkShift] entity itself: [WorkShiftService][uz.safecity.transportobserver.shifts.service.WorkShiftService]
 * is the only place in the codebase that reads/writes `work_shifts` directly (see [WorkShift]
 * kdoc), so callers outside the `shifts` module only ever see this projection.
 */
data class CheckpointOpenShiftDto(
	val inspectorId: UUID,
	val startedAt: Instant
)

package uz.safecity.transportobserver.shifts.dto

import uz.safecity.transportobserver.shifts.entity.WorkShift
import java.time.Instant
import java.util.UUID

/**
 * `POST /api/v1/inspector/me/shift/start`, `POST .../end`, and `GET .../current` response body.
 * [endedAt] is `null` while the shift is still open — see [WorkShift] kdoc.
 */
data class WorkShiftDto(
	val id: UUID,
	val inspectorId: UUID,
	val startedAt: Instant,
	val endedAt: Instant?
) {
	companion object {
		fun from(shift: WorkShift) = WorkShiftDto(
			id = requireNotNull(shift.id),
			inspectorId = shift.inspectorId,
			startedAt = shift.startedAt,
			endedAt = shift.endedAt
		)
	}
}

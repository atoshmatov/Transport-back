package uz.safecity.transportobserver.checkpoints.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

/**
 * `GET /api/v1/checkpoints/{id}/on-duty` row — mobile "Nazorat punkti" screen's "Navbatchi
 * inspektorlar" list. One row per inspector with a currently-open
 * [uz.safecity.transportobserver.shifts.entity.WorkShift] whose
 * [uz.safecity.transportobserver.shifts.entity.WorkShift.checkpointId] matches this checkpoint —
 * see [uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService.getOnDuty].
 *
 * [online] and "on duty" are deliberately NOT the same thing here either (see [WorkShift]
 * kdoc — this whole roster is already scoped to on-duty inspectors, [online] answers the
 * separate "are they actually reachable right now" question on top of that, exactly like
 * [uz.safecity.transportobserver.employees.dto.EmployeeDto.online] /
 * [uz.safecity.transportobserver.map.dto.EmployeeLocationDto.online]): computed the same way as
 * [uz.safecity.transportobserver.map.service.InspectorLocationService.listEmployeeLocations] —
 * session-activity freshness ([uz.safecity.transportobserver.auth.entity.Account.lastActiveAt]) OR
 * GPS-heartbeat freshness ([uz.safecity.transportobserver.map.entity.InspectorLocation.updatedAt]).
 * An inspector who checked into this checkpoint's shift but hasn't touched the app/sent a location
 * in a while shows `online = false` here — that is a real, honest signal, not a bug.
 *
 * [position] mirrors [uz.safecity.transportobserver.employees.entity.Employee.position] verbatim
 * (a free-text field, e.g. "Katta inspektor") — there is no separate "shift type" (kunlik/tungi
 * smena) concept anywhere in this codebase, only [shiftStartedAt]/[shiftEndedAt] timestamps (see
 * [uz.safecity.transportobserver.shifts.entity.WorkShift] kdoc), so this DTO deliberately does not
 * invent a "smena nomi" label — the client can format [shiftStartedAt] itself if it needs a
 * human-readable start time next to the position.
 */
data class CheckpointOnDutyInspectorDto(
	val inspectorId: UUID,
	val fullName: String,
	val position: String?,
	val shiftStartedAt: Instant,
	val online: Boolean,
	val lastSeenAt: Instant?
)

/**
 * `GET /api/v1/checkpoints/{id}/today-stats` — mobile "Nazorat punkti" screen's "BUGUNGI HOLAT"
 * block. See [uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService.getTodayStats]
 * for exactly how each field is computed.
 *
 * [detectedIncidentsCount] and [averageInspectionDurationMinutes] are `null` — NOT `0` — because
 * this codebase genuinely cannot compute either honestly yet:
 * - [uz.safecity.transportobserver.incidents.entity.Incident] has no `checkpointId` column at all
 *   (unlike [uz.safecity.transportobserver.inspections.entity.Inspection], which does), so there is
 *   no way to say "this many incidents were detected AT this checkpoint" without guessing (e.g. by
 *   proximity) — see [CheckpointStatsService] kdoc. Returning `0` here would silently claim "zero
 *   incidents detected", which is a fabricated fact, not an absent one.
 * - [uz.safecity.transportobserver.inspections.entity.Inspection] has no "check started" timestamp
 *   distinct from [uz.safecity.transportobserver.inspections.entity.Inspection.scheduledAt] (when
 *   an admin PLANNED it, not when the inspector actually began performing it) — so
 *   `performedAt - scheduledAt` would measure "how late/early was this compared to when it was
 *   scheduled", not "how long did the inspection take", and labeling that difference "o'rtacha
 *   tekshiruv vaqti" would be misleading, not merely approximate.
 *
 * `@JsonInclude(ALWAYS)` so these two fields always serialize as explicit `null` rather than being
 * omitted — same convention as
 * [uz.safecity.transportobserver.inspector.dto.InspectorCurrentLocationDto], so the mobile client
 * can render an explicit "ma'lumot yo'q" state instead of treating a missing key as a bug.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class CheckpointTodayStatsDto(
	val checkpointId: UUID,
	val onDutyInspectorsCount: Int,
	val inspectionsCompletedTodayCount: Int,
	val detectedIncidentsCount: Int?,
	val averageInspectionDurationMinutes: Double?,
	val computedAt: Instant
)

/**
 * `GET /api/v1/checkpoints/{id}/metrics` — mobile "Nazorat punkti" screen's 3 header metrics
 * ("Tekshiruv/oy", "Aniqlangan holat", "Inspektor"). See
 * [uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService.getMetrics].
 *
 * [detectedCasesCount] is `null` for the same reason as
 * [CheckpointTodayStatsDto.detectedIncidentsCount] — [uz.safecity.transportobserver.incidents.entity.Incident]
 * has no `checkpointId`, so a checkpoint-scoped "aniqlangan holat" count cannot be computed
 * honestly today. [inspectorsThisMonthCount] answers "how many distinct inspectors had at least
 * one inspection task at this checkpoint this month" — NOT "how many are on duty right now" (see
 * [uz.safecity.transportobserver.inspections.repository.InspectionRepository.countDistinctInspectorsByCheckpointIdAndCreatedAtBetween]
 * kdoc); use `GET /{id}/on-duty`'s result size for the real-time headcount instead.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class CheckpointMetricsDto(
	val checkpointId: UUID,
	val inspectionsThisMonthCount: Int,
	val detectedCasesCount: Int?,
	val inspectorsThisMonthCount: Int
)

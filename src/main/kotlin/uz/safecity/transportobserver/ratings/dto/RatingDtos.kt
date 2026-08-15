package uz.safecity.transportobserver.ratings.dto

import uz.safecity.transportobserver.ratings.entity.RatingSnapshot
import java.time.LocalDate
import java.util.UUID

/**
 * `GET /api/v1/ratings/top` row. [employeeId] is the
 * [uz.safecity.transportobserver.employees.entity.Employee.id] linked to the inspector's
 * account — NOT [uz.safecity.transportobserver.auth.entity.Account.id] (see
 * [uz.safecity.transportobserver.ratings.service.RatingService] kdoc for why the ranking is
 * computed by account id internally but surfaced here by employee id).
 */
data class InspectorRatingDto(
	val employeeId: UUID,
	val fullName: String,
	val completedInspectionsCount: Int,
	val rank: Int
)

/**
 * `GET /api/v1/ratings/me` body. See
 * [uz.safecity.transportobserver.ratings.service.RatingService.getMyRating] kdoc for exactly
 * which roles get a body here vs. an empty `204 No Content` response.
 */
data class MyRatingDto(
	val employeeId: UUID,
	val fullName: String,
	val completedInspectionsCount: Int,
	/** Null when the caller doesn't rank inside [uz.safecity.transportobserver.ratings.service.RatingService]'s top list — still their own real stats, just no rank assigned. */
	val rank: Int?
)

/**
 * `GET /api/v1/ratings/me/history` row — mobile Profile screen "reyting dinamikasi" chart. See
 * [RatingSnapshot] kdoc for why this carries [completedInspectionsCount] rather than an invented
 * 0-5 score, and why the list is typically empty today (no scheduled job populates it yet — MVP
 * table, not a bug).
 */
data class RatingSnapshotDto(
	val snapshotDate: LocalDate,
	val completedInspectionsCount: Int,
	val rank: Int?
) {
	companion object {
		fun from(snapshot: RatingSnapshot) = RatingSnapshotDto(
			snapshotDate = snapshot.snapshotDate,
			completedInspectionsCount = snapshot.completedInspectionsCount,
			rank = snapshot.rank
		)
	}
}

package uz.safecity.transportobserver.ratings.dto

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

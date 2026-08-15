package uz.safecity.transportobserver.ratings.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

/**
 * A single point-in-time snapshot of one inspector's leaderboard standing (TZ mobile Profile
 * screen "reyting dinamikasi" chart — `GET /api/v1/ratings/me/history`). See
 * [uz.safecity.transportobserver.ratings.service.RatingService] kdoc for why [rank] is nullable
 * (same "not every inspector shows up in the top list" reasoning as `MyRatingDto.rank`) and why
 * this stores [completedInspectionsCount] rather than some invented 0-5 "star rating" — the
 * leaderboard's only defined metric today is the completed-inspection count, so a snapshot table
 * should capture that real number, not a fabricated score nothing in the backend computes.
 *
 * MVP scope (per product decision on this task): this table exists and is queryable, but nothing
 * writes to it yet. A future scheduled job (e.g. a daily `@Scheduled` task reusing
 * [uz.safecity.transportobserver.ratings.service.RatingService]'s ranking) is expected to insert
 * one row per active inspector per day — until then, [uz.safecity.transportobserver.ratings.service.RatingService.getMyRatingHistory]
 * simply returns whatever rows exist (typically none), which the mobile client renders as an
 * empty chart rather than a hardcoded fake trend line.
 *
 * [accountId] (not [uz.safecity.transportobserver.employees.entity.Employee.id]) to match every
 * other inspector-scoping column in the codebase (`Incident.assignedInspectorId`,
 * `Inspection.assignedInspectorId`) — see those entities' kdoc for why the account id, not the
 * employee id, is the right key for anything compared against `CustomUserDetails.accountId`.
 */
@Entity
@Table(name = "rating_snapshot")
class RatingSnapshot(

	@Column(name = "account_id", nullable = false)
	var accountId: UUID,

	@Column(name = "completed_inspections_count", nullable = false)
	var completedInspectionsCount: Int,

	/** Null when the inspector didn't rank inside the top list on this [snapshotDate] — mirrors `MyRatingDto.rank`. */
	var rank: Int? = null,

	@Column(name = "snapshot_date", nullable = false)
	var snapshotDate: LocalDate

) : BaseEntity()

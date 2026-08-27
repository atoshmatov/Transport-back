package uz.safecity.transportobserver.inspections.repository

import uz.safecity.transportobserver.common.dto.DailyCountProjection
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * [JpaSpecificationExecutor] backs the filtered/paginated `GET /inspections`
 * (see InspectionService#list + #buildSpecification) — same pattern as
 * IncidentRepository/CheckpointRepository.
 */
interface InspectionRepository : JpaRepository<Inspection, UUID>, JpaSpecificationExecutor<Inspection> {

	/**
	 * INSPECTOR scoping for single-record reads/updates: the id+ownership check
	 * happens in ONE query so a foreign inspection id simply doesn't match
	 * (empty Optional) instead of matching-then-being-rejected — surfaces as a
	 * 404, never a 403, so probing other inspectors' inspection ids by URL
	 * can't even confirm they exist. Mirrors IncidentRepository.findByIdAndAssignedInspectorId.
	 */
	fun findByIdAndAssignedInspectorId(id: UUID, assignedInspectorId: UUID): Optional<Inspection>

	/**
	 * Inspector panel `openInspectionsCount` — PLANNED + IN_PROGRESS tasks assigned to the
	 * caller. A DB-level count (unlike InspectorPanelService's Incident-era approach of loading
	 * every assigned row into memory) since this is the only thing the panel needs from it.
	 */
	fun countByAssignedInspectorIdAndStatusIn(assignedInspectorId: UUID, statuses: Collection<InspectionStatus>): Long

	/** Inspector panel `completedTodayCount` — COMPLETED tasks whose [Inspection.performedAt] falls within today's [start, end) window. */
	fun countByAssignedInspectorIdAndStatusAndPerformedAtBetween(
		assignedInspectorId: UUID,
		status: InspectionStatus,
		start: Instant,
		end: Instant
	): Long

	/** Inspector panel "recent inspections" widget — the caller's most recently touched tasks, any status. */
	fun findTop5ByAssignedInspectorIdOrderByUpdatedAtDesc(assignedInspectorId: UUID): List<Inspection>

	/**
	 * Mobile "Xodim kartasi" profile-detail screen's "SO'NGGI FAOLIYAT" timeline
	 * ([uz.safecity.transportobserver.inspector.service.ProfileDetailService]) — the caller's most
	 * recently COMPLETED inspections, ordered by [Inspection.performedAt] (always non-null once
	 * COMPLETED, see [Inspection] class kdoc), not [BaseEntity.updatedAt] like
	 * [findTop5ByAssignedInspectorIdOrderByUpdatedAtDesc] — this timeline answers "when did the
	 * inspector actually finish this", not "when was this row last touched".
	 */
	fun findTop5ByAssignedInspectorIdAndStatusOrderByPerformedAtDesc(
		assignedInspectorId: UUID,
		status: InspectionStatus
	): List<Inspection>

	/** Reports dashboard `todayInspectionsCount` (ReportService) — system-wide, not scoped to one inspector. */
	fun countByStatusAndPerformedAtBetween(status: InspectionStatus, start: Instant, end: Instant): Long

	/**
	 * Checkpoint-detail screen's "bugungi holat" (today) / "Tekshiruv/oy" (this month) counters —
	 * see [uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService.getTodayStats] /
	 * [uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService.getMetrics]. Same
	 * shape as [countByStatusAndPerformedAtBetween], just additionally scoped to one checkpoint.
	 */
	fun countByCheckpointIdAndStatusAndPerformedAtBetween(
		checkpointId: UUID,
		status: InspectionStatus,
		start: Instant,
		end: Instant
	): Long

	/**
	 * Ratings `me`/`top` completed-count (RatingService) — system-wide count for a single inspector
	 * account, unlike [countByAssignedInspectorIdAndStatusIn] which takes a status collection for the
	 * inspector panel's PLANNED+IN_PROGRESS "open" count.
	 */
	fun countByAssignedInspectorIdAndStatus(assignedInspectorId: UUID, status: InspectionStatus): Long

	/**
	 * Reports `activity` widget (ReportService) — daily count of inspections *created* within
	 * `[start, end)`, grouped by local calendar day in `Asia/Tashkent` (native query: JPQL has no
	 * portable day-truncation function, and this needs to match the zone every other "today"/"day
	 * window" calculation in the codebase uses — see InspectorPanelService.APP_ZONE kdoc). A single
	 * `GROUP BY` query rather than pulling raw timestamps and bucketing in Kotlin, per TZ.
	 *
	 * Uses `createdAt` (always non-null, set the moment an admin/operator schedules the task) rather
	 * than `performedAt` (null until COMPLETED) — deliberately answers "how many inspection tasks
	 * entered the system that day", a daily-throughput metric, not "how many were finished that day"
	 * (that narrower question is what the dashboard's `todayInspectionsCount` already answers).
	 */
	@Query(
		value = """
			select (created_at at time zone 'Asia/Tashkent')::date as day, count(*) as cnt
			from inspections
			where created_at >= :start and created_at < :end
			group by day
			order by day
		""",
		nativeQuery = true
	)
	fun countDailyCreatedBetween(@Param("start") start: Instant, @Param("end") end: Instant): List<DailyCountProjection>

	/**
	 * Ratings leaderboard (RatingService#fullRanking) — COMPLETED inspection counts grouped by
	 * [Inspection.assignedInspectorId], which is an Account id, not an Employee id (see Inspection
	 * kdoc). One `GROUP BY` query for every inspector's count, instead of one `COUNT` query per
	 * inspector (N+1).
	 */
	@Query("select i.assignedInspectorId as accountId, count(i) as cnt from Inspection i where i.status = :status and i.assignedInspectorId is not null group by i.assignedInspectorId")
	fun countCompletedGroupByInspector(@Param("status") status: InspectionStatus): List<InspectorCompletedCountProjection>

	/**
	 * Checkpoint-detail screen's "Inspektor" metric
	 * ([uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService.getMetrics]) —
	 * count of DISTINCT inspectors assigned at least one [Inspection] at [checkpointId] whose
	 * [Inspection.createdAt] falls in `[start, end)` (the current month). Uses `createdAt`, not
	 * `scheduledAt`/`performedAt`, for the same reason as [countDailyCreatedBetween]: it's the one
	 * timestamp that's always non-null and reliably answers "was this task on the books this
	 * month", regardless of whether it's been performed yet. Answers "how many distinct inspectors
	 * touched this checkpoint this month", NOT "how many are on duty right now" — that's a
	 * different, real-time question answered by
	 * [uz.safecity.transportobserver.shifts.service.WorkShiftService.openShiftsForCheckpoint]
	 * instead.
	 */
	@Query(
		"""
			select count(distinct i.assignedInspectorId) from Inspection i
			where i.checkpointId = :checkpointId
			and i.assignedInspectorId is not null
			and i.createdAt >= :start and i.createdAt < :end
		"""
	)
	fun countDistinctInspectorsByCheckpointIdAndCreatedAtBetween(
		@Param("checkpointId") checkpointId: UUID,
		@Param("start") start: Instant,
		@Param("end") end: Instant
	): Long
}

/**
 * See [InspectionRepository.countCompletedGroupByInspector]. [accountId] is
 * [uz.safecity.transportobserver.auth.entity.Account.id] — kept local to this file (rather than in
 * common.dto with [DailyCountProjection]/[uz.safecity.transportobserver.common.dto.RegionCountProjection])
 * since, unlike those two, this shape isn't reused by any other repository.
 */
interface InspectorCompletedCountProjection {
	val accountId: UUID
	val cnt: Long
}

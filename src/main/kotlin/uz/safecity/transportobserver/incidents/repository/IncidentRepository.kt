package uz.safecity.transportobserver.incidents.repository

import uz.safecity.transportobserver.common.dto.DailyCountProjection
import uz.safecity.transportobserver.common.dto.MonthlyCountProjection
import uz.safecity.transportobserver.common.dto.RegionCountProjection
import uz.safecity.transportobserver.incidents.entity.ActionType
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * [JpaSpecificationExecutor] backs the filtered/paginated `GET /incidents`
 * (see IncidentService#list + #buildSpecification) — same pattern as
 * EmployeeRepository/CheckpointRepository.
 */
interface IncidentRepository : JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

	/** INSPECTOR scoping: "only my assigned incidents" (see IncidentService.list). */
	fun findByAssignedInspectorId(assignedInspectorId: UUID): List<Incident>

	/** Offline-sync dedup lookup for `POST /incidents` — see IncidentService#create kdoc. */
	fun findByClientUuid(clientUuid: UUID): Incident?

	/**
	 * INSPECTOR scoping for single-record reads: the id+ownership check happens
	 * in ONE query so a foreign incident id simply doesn't match (empty
	 * Optional) instead of matching-then-being-rejected — the controller/service
	 * turns that into a 404, not a 403, so probing other inspectors' incident
	 * ids by URL can't even confirm they exist (see IncidentService.getById).
	 */
	fun findByIdAndAssignedInspectorId(id: UUID, assignedInspectorId: UUID): Optional<Incident>

	/**
	 * Reports dashboard `detectedViolationsCount` (ReportStatsService) — ALL-time count (any
	 * [IncidentStatus]) of incidents whose [IncidentType] is in the "aniqlangan holatlar" bucket
	 * (VIOLATION/TECHNICAL_FAULT/OTHER). See [ReportStatsService.getDashboard] kdoc for why the
	 * type set is split this way and why the two dashboard incident counts never overlap.
	 */
	fun countByTypeIn(types: Collection<IncidentType>): Long

	/**
	 * Reports dashboard `emergencyIncidentsCount` (ReportStatsService) — incidents whose
	 * [IncidentType] is in the "favqulodda hodisalar" bucket (ACCIDENT/SECURITY) AND whose
	 * [IncidentStatus] is still open (NEW/IN_PROGRESS) — mirrors the old `countByStatusIn`
	 * ("still needs attention") but scoped to just the emergency types, since the dashboard card
	 * this backs is explicitly "Faol holatlar" (active emergency cases), not every open incident.
	 */
	fun countByTypeInAndStatusIn(types: Collection<IncidentType>, statuses: Collection<IncidentStatus>): Long

	/** Reports `regions-distribution` screen (ReportService) — see [RegionCountProjection] kdoc. */
	@Query("select i.regionName as regionName, count(i) as cnt from Incident i group by i.regionName")
	fun countGroupByRegion(): List<RegionCountProjection>

	/**
	 * Reports `activity` widget (ReportService) — sibling of
	 * [uz.safecity.transportobserver.inspections.repository.InspectionRepository.countDailyCreatedBetween],
	 * same reasoning (native `GROUP BY`, `Asia/Tashkent` zone, `createdAt` not `occurredAt` — an
	 * incident's `occurredAt` is caller-reported and can be null/backdated, whereas `createdAt` is the
	 * reliable "this hit the system on this day" timestamp).
	 */
	@Query(
		value = """
			select (created_at at time zone 'Asia/Tashkent')::date as day, count(*) as cnt
			from incidents
			where created_at >= :start and created_at < :end
			group by day
			order by day
		""",
		nativeQuery = true
	)
	fun countDailyCreatedBetween(@Param("start") start: Instant, @Param("end") end: Instant): List<DailyCountProjection>

	/**
	 * Reports `activity` widget's `range=1y` window (ReportStatsService) — sibling of
	 * [uz.safecity.transportobserver.inspections.repository.InspectionRepository.countMonthlyCreatedBetween],
	 * same reasoning (native `GROUP BY` month, `Asia/Tashkent` zone, `createdAt` not `occurredAt`).
	 */
	@Query(
		value = """
			select date_trunc('month', created_at at time zone 'Asia/Tashkent')::date as month, count(*) as cnt
			from incidents
			where created_at >= :start and created_at < :end
			group by month
			order by month
		""",
		nativeQuery = true
	)
	fun countMonthlyCreatedBetween(@Param("start") start: Instant, @Param("end") end: Instant): List<MonthlyCountProjection>

	/**
	 * Mobile Profile screen "hodisa turi bo'yicha" donut (InspectorPanelService#getIncidentTypeBreakdown)
	 * — [IncidentType] counts scoped to a single inspector's own assigned incidents (any [IncidentStatus]).
	 * One `GROUP BY` query rather than one `COUNT` per [IncidentType] value.
	 */
	@Query("select i.type as type, count(i) as cnt from Incident i where i.assignedInspectorId = :inspectorId group by i.type")
	fun countGroupByTypeForInspector(@Param("inspectorId") inspectorId: UUID): List<IncidentTypeCountProjection>

	/**
	 * Mobile Profile screen "harakat turi bo'yicha" donut + the `GET /api/v1/inspector/me/stats`
	 * 4 action-count metrics (InspectorPanelService#getActionTypeBreakdown / #getMyStats) —
	 * [ActionType] counts scoped to a single inspector's own assigned incidents. [ActionType]
	 * itself is nullable on [Incident] (legacy rows predating the field — see that entity's
	 * kdoc), so `actionType` can be null here too; callers filter/bucket that out rather than
	 * fabricating a count for "unspecified".
	 */
	@Query("select i.actionType as actionType, count(i) as cnt from Incident i where i.assignedInspectorId = :inspectorId group by i.actionType")
	fun countGroupByActionTypeForInspector(@Param("inspectorId") inspectorId: UUID): List<IncidentActionTypeCountProjection>

	/** Mobile Profile screen "so'nggi ishlar" widget — the caller's most recently created incident reports. */
	fun findTop10ByAssignedInspectorIdOrderByCreatedAtDesc(assignedInspectorId: UUID): List<Incident>

	/**
	 * `POST /reports` INCIDENTS_SUMMARY generation (ReportGenerationService) — every incident
	 * reported within the report's `[periodStart, periodEnd)` window, any status/type. Uses
	 * `createdAt`, not `occurredAt`, for the same reliability reason as
	 * [countDailyCreatedBetween]'s kdoc: `occurredAt` is caller-reported and can be null/backdated.
	 */
	fun findByCreatedAtBetween(start: Instant, end: Instant): List<Incident>

	/**
	 * Mobile "Transport vositasi" (vehicleDetail) screen's violation history —
	 * `GET /api/v1/inspector/vehicles/{id}` (InspectorPanelService#getVehicleDetail). Deliberately
	 * NOT scoped to any one inspector (contrast [findTop10ByAssignedInspectorIdOrderByCreatedAtDesc]) —
	 * this is the *vehicle's* record across whichever inspector(s) filed a report against it via
	 * [Incident.vehicleId]. Ordered by `createdAt`, not `occurredAt`, for the same reliability
	 * reason as [countDailyCreatedBetween]'s kdoc: `occurredAt` is caller-reported and can be
	 * null/backdated, whereas `createdAt` is always set. `Top20` (not paginated) mirrors
	 * [findTop10ByAssignedInspectorIdOrderByCreatedAtDesc] — a simple recent-history list is enough
	 * for this screen, same as that one.
	 */
	fun findTop20ByVehicleIdOrderByCreatedAtDesc(vehicleId: UUID): List<Incident>

	/**
	 * Real, FK-backed "incidents linked to this checkpoint" count for
	 * [uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService]'s `today-stats`/
	 * `metrics` endpoints (the map's per-checkpoint "xavf darajasi" rollup) — replaces the previous
	 * always-`null` placeholder now that [Incident.checkpointId] is a real FK (see that field's
	 * kdoc for the "never a proximity guess" rule this still honors: this simply counts whatever
	 * [Incident.checkpointId] values were explicitly confirmed by a caller, nothing invented here).
	 * `createdAt`, not `occurredAt`, for the same reliability reason as [countDailyCreatedBetween]'s
	 * kdoc.
	 */
	fun countByCheckpointIdAndCreatedAtBetween(checkpointId: UUID, start: Instant, end: Instant): Long
}

/** See [IncidentRepository.countGroupByTypeForInspector]. */
interface IncidentTypeCountProjection {
	val type: IncidentType
	val cnt: Long
}

/** See [IncidentRepository.countGroupByActionTypeForInspector]. */
interface IncidentActionTypeCountProjection {
	val actionType: ActionType?
	val cnt: Long
}

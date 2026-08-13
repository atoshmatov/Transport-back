package uz.safecity.transportobserver.incidents.repository

import uz.safecity.transportobserver.common.dto.DailyCountProjection
import uz.safecity.transportobserver.common.dto.RegionCountProjection
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
}

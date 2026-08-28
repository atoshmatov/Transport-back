package uz.safecity.transportobserver.checkpoints.repository

import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import uz.safecity.transportobserver.common.dto.RegionCountProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CheckpointRepository : JpaRepository<Checkpoint, UUID>, JpaSpecificationExecutor<Checkpoint> {

	/** Backs `GET /api/v1/map/checkpoints` (MapController) — only active checkpoints belong on the map. */
	fun findByIsActiveTrue(): List<Checkpoint>

	/** Backs `DashboardSummaryDto.activeCheckpointsCount` (InspectorPanelService). */
	fun countByIsActiveTrue(): Long

	/** Reports `regions-distribution` screen (ReportService) — see [RegionCountProjection] kdoc. */
	@Query("select c.regionName as regionName, count(c) as cnt from Checkpoint c group by c.regionName")
	fun countGroupByRegion(): List<RegionCountProjection>

	/**
	 * Reports/map `checkpoints-distribution` widget (ReportStatsService) — active checkpoints
	 * grouped by [Checkpoint.type]. Only [isActive] ones count, matching [findByIsActiveTrue]
	 * (the same set the public map view shows) — a deactivated checkpoint shouldn't inflate a
	 * category card the map/dashboard is implicitly summarizing.
	 *
	 * [Checkpoint.type] is a free-form string today (no enum yet, see entity kdoc) — this simply
	 * groups whatever values actually exist in the column; it does NOT invent or normalize
	 * category names. If no row has been tagged with a real category yet, every active checkpoint
	 * falls into the null/"Belgilanmagan" bucket (see ReportStatsService).
	 */
	@Query("select c.type as type, count(c) as cnt from Checkpoint c where c.isActive = true group by c.type")
	fun countActiveGroupByType(): List<TypeCountProjection>

	/**
	 * `GET /api/v1/checkpoints/nearby` — the mobile "Nazorat punkti" nearest-checkpoint SUGGESTION
	 * (see [uz.safecity.transportobserver.incidents.entity.Incident.checkpointId] kdoc for the full
	 * "hybrid approach" reasoning this backs): a pure read-only ranking, never a write, never
	 * auto-applied to anything — the client shows these as a pre-selectable-but-editable list, the
	 * user still has to confirm/change the choice before it's ever sent back as
	 * [uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest.checkpointId].
	 *
	 * Native PostGIS query (Hibernate/JPQL has no `ST_Distance` function): casts both the stored
	 * `geometry(Point,4326)` column and the query point to `geography` so `ST_Distance` returns
	 * meters on the sphere rather than raw degrees in the planar SRID 4326 CRS (a planar distance in
	 * degrees would be meaningless as "how many meters away"). Only [Checkpoint.isActive] rows are
	 * eligible — same "active checkpoints only" convention as [findByIsActiveTrue]/
	 * [countActiveGroupByType]: a deactivated checkpoint shouldn't be suggested to a field inspector.
	 */
	@Query(
		value = """
			select c.id as id,
				c.name as name,
				st_y(c.location) as latitude,
				st_x(c.location) as longitude,
				st_distance(
					c.location::geography,
					st_setsrid(st_makepoint(:longitude, :latitude), 4326)::geography
				) as distanceMeters
			from checkpoints c
			where c.is_active = true
			order by distanceMeters asc
			limit :limit
		""",
		nativeQuery = true
	)
	fun findNearestActive(
		@Param("latitude") latitude: Double,
		@Param("longitude") longitude: Double,
		@Param("limit") limit: Int
	): List<CheckpointNearbyProjection>
}

/** See [CheckpointRepository.findNearestActive]. */
interface CheckpointNearbyProjection {
	val id: UUID
	val name: String
	val latitude: Double
	val longitude: Double
	val distanceMeters: Double
}

/**
 * See [CheckpointRepository.countActiveGroupByType]. Kept local to this file — same reasoning as
 * [uz.safecity.transportobserver.inspections.repository.InspectorCompletedCountProjection]: this
 * shape isn't reused by any other repository, unlike [RegionCountProjection]/
 * [uz.safecity.transportobserver.common.dto.DailyCountProjection].
 */
interface TypeCountProjection {
	val type: String?
	val cnt: Long
}

package uz.safecity.transportobserver.checkpoints.repository

import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import uz.safecity.transportobserver.common.dto.RegionCountProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
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

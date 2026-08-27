package uz.safecity.transportobserver.map.repository

import uz.safecity.transportobserver.map.entity.InspectorLocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface InspectorLocationRepository : JpaRepository<InspectorLocation, UUID> {
	fun findByInspectorId(inspectorId: UUID): InspectorLocation?

	/**
	 * Batched sibling of [findByInspectorId] — backs
	 * [uz.safecity.transportobserver.checkpoints.service.CheckpointStatsService.getOnDuty], which
	 * needs the GPS-heartbeat signal for a whole checkpoint's on-duty roster in one query instead
	 * of one `findByInspectorId` call per row (same N+1-avoidance pattern as
	 * [uz.safecity.transportobserver.auth.repository.AccountRepository.findByEmployeeIdIn]).
	 */
	fun findByInspectorIdIn(inspectorIds: Collection<UUID>): List<InspectorLocation>

	/**
	 * Atomic upsert backing
	 * [uz.safecity.transportobserver.map.service.InspectorLocationService.upsertMyLocation] —
	 * replaces the previous find-then-save (`findByInspectorId` + `save`), which raced under
	 * concurrent calls for the same inspector (e.g. a mobile client firing a retry): both requests
	 * could read `null`, both `INSERT`, and the loser would fail the
	 * `uq_inspector_locations_inspector_id` unique constraint with an unhandled
	 * `DataIntegrityViolationException` -> opaque 500. `INSERT ... ON CONFLICT` folds the
	 * "does a row exist" check and the write into one statement, so Postgres itself serializes the
	 * two concurrent calls instead of the app racing on it.
	 *
	 * Deliberately bypasses JPA's `@Version` optimistic-lock column ([BaseEntity]) — this is a
	 * single scalar overwrite (last-write-wins location heartbeat), not a find-then-modify-then-save
	 * flow, so there is no lost update to detect. `version` is still bumped on conflict so any
	 * in-memory (now-stale) entity held elsewhere still gets an `OptimisticLockingFailureException`
	 * on its next save instead of silently clobbering this write.
	 */
	@Modifying
	@Query(
		value = """
			insert into inspector_locations (id, inspector_id, location, created_at, updated_at, version)
			values (:id, :inspectorId, st_setsrid(st_makepoint(:longitude, :latitude), 4326), :now, :now, 0)
			on conflict (inspector_id) do update
			set location = excluded.location,
				updated_at = excluded.updated_at,
				version = inspector_locations.version + 1
		""",
		nativeQuery = true
	)
	fun upsertLocation(
		@Param("id") id: UUID,
		@Param("inspectorId") inspectorId: UUID,
		@Param("latitude") latitude: Double,
		@Param("longitude") longitude: Double,
		@Param("now") now: Instant
	)
}

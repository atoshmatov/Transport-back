package uz.safecity.transportobserver.shifts.repository

import uz.safecity.transportobserver.shifts.entity.WorkShift
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface WorkShiftRepository : JpaRepository<WorkShift, UUID> {

	/**
	 * The caller's single open shift (if any) — see [WorkShift] kdoc re: at most one open shift
	 * per inspector at a time. Backs end (the row to close)/current. NOT used to guard start
	 * anymore — see [startIfNoOpenShift].
	 */
	fun findByInspectorIdAndEndedAtIsNull(inspectorId: UUID): WorkShift?

	/**
	 * Atomic "start a shift only if the inspector has no open one" backing
	 * [uz.safecity.transportobserver.shifts.service.WorkShiftService.startShift] — replaces the
	 * previous find-then-save (`findByInspectorIdAndEndedAtIsNull` + `save`), which raced under
	 * concurrent calls for the same inspector (e.g. a mobile client retrying after a network
	 * timeout): both requests could read "no open shift", both `INSERT`, and the "at most one open
	 * shift per inspector" rule silently broke. Same fix shape as
	 * [uz.safecity.transportobserver.map.repository.InspectorLocationRepository.upsertLocation] —
	 * `INSERT ... ON CONFLICT` folds the "does an open shift already exist" check and the write
	 * into one statement Postgres itself serializes, instead of the app racing on it.
	 *
	 * The `ON CONFLICT (inspector_id) WHERE ended_at IS NULL` target matches the partial unique
	 * index created by
	 * [uz.safecity.transportobserver.shifts.config.WorkShiftSchemaInitializer] — Hibernate's
	 * `ddl-auto: update` cannot create a *partial* unique index on its own, so that initializer
	 * bootstraps it at startup. Without that index this statement fails at query time.
	 *
	 * Returns the number of rows inserted: `1` on success, `0` if the inspector already had an
	 * open shift (caller must translate that into a `ConflictException`, mirroring the old
	 * behavior) — `DO NOTHING` means a conflict is a normal, non-throwing outcome here, not an
	 * error.
	 */
	@Modifying
	@Query(
		value = """
			insert into work_shifts (id, inspector_id, started_at, created_at, updated_at, version)
			values (:id, :inspectorId, :startedAt, :now, :now, 0)
			on conflict (inspector_id) where ended_at is null do nothing
		""",
		nativeQuery = true
	)
	fun startIfNoOpenShift(
		@Param("id") id: UUID,
		@Param("inspectorId") inspectorId: UUID,
		@Param("startedAt") startedAt: Instant,
		@Param("now") now: Instant
	): Int

	/**
	 * Batch "who is currently on duty" lookup backing
	 * [uz.safecity.transportobserver.map.dto.EmployeeLocationDto.onDuty] (`GET /api/v1/map/employees`)
	 * and [uz.safecity.transportobserver.employees.dto.EmployeeDto.onDuty] (`GET /api/v1/employees`)
	 * — one query for the whole page/list instead of one `findByInspectorIdAndEndedAtIsNull` call
	 * per row (N+1), same batching pattern as every other list-enrichment in this codebase (e.g.
	 * IncidentService#list's evidence-count lookup).
	 */
	fun findByInspectorIdInAndEndedAtIsNull(inspectorIds: Collection<UUID>): List<WorkShift>

	/**
	 * Every still-open shift that started before [cutoff] — backs
	 * [uz.safecity.transportobserver.shifts.service.WorkShiftCleanupJob], which auto-closes shifts
	 * an inspector never explicitly ended (e.g. the mobile app was killed/lost connectivity
	 * without calling `end`). See that class's kdoc for the staleness threshold and why.
	 */
	fun findByEndedAtIsNullAndStartedAtBefore(cutoff: Instant): List<WorkShift>
}

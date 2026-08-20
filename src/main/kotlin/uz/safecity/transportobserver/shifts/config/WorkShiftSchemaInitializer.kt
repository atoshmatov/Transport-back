package uz.safecity.transportobserver.shifts.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Creates the partial unique index backing "at most one open shift per inspector"
 * (see [uz.safecity.transportobserver.shifts.entity.WorkShift] kdoc) at application startup.
 *
 * This codebase has no Flyway/Liquibase — schema is otherwise entirely `ddl-auto: update`
 * (see `application-dev.yml`), which is sufficient for plain columns/full unique constraints
 * (e.g. `uq_inspector_locations_inspector_id` via `@Table(uniqueConstraints = ...)` on
 * [uz.safecity.transportobserver.map.entity.InspectorLocation]) but CANNOT express a *partial*
 * index (`WHERE ended_at IS NULL`) through JPA annotations — Hibernate has no annotation for a
 * conditional unique constraint. A raw bootstrap statement is the minimal way to get this
 * DB-level guarantee without introducing a full migration tool for one index.
 *
 * Runs after Hibernate's own schema generation (`ApplicationRunner`s execute once the context is
 * fully refreshed, i.e. after `ddl-auto: update` has already created `work_shifts`), and is
 * idempotent (`IF NOT EXISTS`) so it's safe to run on every startup, same as
 * [uz.safecity.transportobserver.common.config.DefaultDataSeeder]'s idempotent seeding checks.
 *
 * [WorkShiftRepository.startIfNoOpenShift][uz.safecity.transportobserver.shifts.repository.WorkShiftRepository.startIfNoOpenShift]
 * is the `INSERT ... ON CONFLICT (inspector_id) WHERE ended_at IS NULL` that relies on this index
 * existing — without it, that statement fails at query time with "there is no unique or exclusion
 * constraint matching the ON CONFLICT specification".
 */
@Component
@Order(0)
class WorkShiftSchemaInitializer(
	private val jdbcTemplate: JdbcTemplate
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(WorkShiftSchemaInitializer::class.java)

	override fun run(args: ApplicationArguments) {
		jdbcTemplate.execute(
			"""
				create unique index if not exists ux_work_shifts_inspector_open
				on work_shifts (inspector_id)
				where ended_at is null
			"""
		)
		log.debug("work_shifts: ux_work_shifts_inspector_open partial unique index tayyor.")
	}
}

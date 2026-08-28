package uz.safecity.transportobserver.employees.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Creates the partial unique index backing "at most one open position-history spell per
 * employee" (see [uz.safecity.transportobserver.employees.entity.EmployeePositionHistory] kdoc) at
 * application startup.
 *
 * This codebase has no Flyway/Liquibase — schema is otherwise entirely `ddl-auto: update` (see
 * `application-dev.yml`), which is sufficient for plain columns/tables (both
 * `employees`' new HR columns and the `employee_position_history` table itself are created this
 * way, no bootstrap needed for those) but CANNOT express a *partial* index
 * (`WHERE ended_at IS NULL`) through JPA annotations. Same gap, same fix shape, as
 * [uz.safecity.transportobserver.shifts.config.WorkShiftSchemaInitializer] — see that class's kdoc
 * for the full rationale for not introducing a migration tool for one index.
 *
 * Runs after Hibernate's own schema generation (`ApplicationRunner`s execute once the context is
 * fully refreshed, i.e. after `ddl-auto: update` has already created `employee_position_history`),
 * and is idempotent (`IF NOT EXISTS`) so it's safe on every startup.
 */
@Component
@Order(0)
class EmployeePositionHistorySchemaInitializer(
	private val jdbcTemplate: JdbcTemplate
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(EmployeePositionHistorySchemaInitializer::class.java)

	override fun run(args: ApplicationArguments) {
		jdbcTemplate.execute(
			"""
				create unique index if not exists ux_employee_position_history_employee_open
				on employee_position_history (employee_id)
				where ended_at is null
			"""
		)
		log.debug("employee_position_history: ux_employee_position_history_employee_open partial unique index tayyor.")
	}
}

package uz.safecity.transportobserver.employees.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One row per "spell" an [Employee] spent in a given [position]/[regionName] combination — the
 * lavozim/hudud o'zgarish jurnali (position/region change log) backing
 * `GET /api/v1/admin/employees/{id}/position-history`. Written automatically by
 * [uz.safecity.transportobserver.employees.service.EmployeePositionHistoryService] whenever
 * [Employee.position]/[Employee.regionName] changes via
 * [uz.safecity.transportobserver.employees.service.EmployeeService.update] — never edited
 * directly by an admin, this is a derived audit trail, not a form.
 *
 * [employeeId] is a plain FK column, no mapped JPA relation — same convention as
 * [uz.safecity.transportobserver.auth.entity.Account.employeeId] (see that entity's kdoc).
 *
 * At most one row per employee may have [endedAt] = null at a time ("the current spell") — same
 * "one open row" shape as [uz.safecity.transportobserver.shifts.entity.WorkShift.endedAt], and
 * guarded the same way: a partial unique index
 * (`ux_employee_position_history_employee_open` on `(employee_id) WHERE ended_at IS NULL`)
 * bootstrapped at startup by
 * [uz.safecity.transportobserver.employees.config.EmployeePositionHistorySchemaInitializer], since
 * Hibernate's `ddl-auto: update` cannot express a *partial* unique constraint through JPA
 * annotations alone (same reasoning as [uz.safecity.transportobserver.shifts.config.WorkShiftSchemaInitializer]
 * kdoc). Unlike `work_shifts`' hot-path concurrent starts, position changes are a low-frequency
 * admin-only write, so [EmployeePositionHistoryService] closes the old row and inserts the new one
 * as two plain JPA statements inside one `@Transactional` method rather than a native
 * `INSERT ... ON CONFLICT` — the index is a DB-level safety net, not the primary concurrency
 * control here.
 *
 * [position] is a free-text snapshot of [Employee.position] at the time, NOT a FK to
 * [uz.safecity.transportobserver.positions.entity.EmployeePosition] (that entity is an unrelated
 * lookup/catalog of position *names* an admin picks from — see its kdoc) — this row records what
 * the employee's `position` string actually was, so it stays a correct historical record even if
 * the catalog entry is later renamed or removed.
 */
@Entity
@Table(name = "employee_position_history")
class EmployeePositionHistory(

	@Column(name = "employee_id", nullable = false)
	var employeeId: UUID,

	@Column(nullable = false)
	var position: String,

	@Column(name = "region_name")
	var regionName: String? = null,

	@Column(name = "started_at", nullable = false)
	var startedAt: Instant,

	@Column(name = "ended_at")
	var endedAt: Instant? = null

) : BaseEntity()

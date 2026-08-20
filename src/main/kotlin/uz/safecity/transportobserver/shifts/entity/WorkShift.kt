package uz.safecity.transportobserver.shifts.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A single ish-smena (work shift) — an INSPECTOR's own "ishga chiqdim" / "ishni tugatdim"
 * check-in/check-out record (MVP scope). Deliberately separate from the session-presence signal
 * ([uz.safecity.transportobserver.auth.entity.Account.lastActiveAt]) and the GPS-heartbeat signal
 * ([uz.safecity.transportobserver.map.entity.InspectorLocation.updatedAt]) that back `online`
 * elsewhere in this codebase (see [uz.safecity.transportobserver.employees.dto.EmployeeDto.online]
 * / [uz.safecity.transportobserver.map.dto.EmployeeLocationDto.online] kdocs): those answer "is
 * this account currently authenticated/reachable", this answers "did this inspector explicitly
 * declare they're on the clock right now" — an inspector can be online (logged into the mobile
 * app, or even mid-shift on the web panel) without ever having started a shift, so `online` and
 * `onDuty` are two independent booleans on the admin views, never conflated.
 * [uz.safecity.transportobserver.shifts.service.WorkShiftService] is the only place that
 * reads/writes this table.
 *
 * [inspectorId] is an [uz.safecity.transportobserver.auth.entity.Account] id, same convention as
 * every other "who is the acting inspector" column in this codebase
 * ([uz.safecity.transportobserver.incidents.entity.Incident.assignedInspectorId],
 * [uz.safecity.transportobserver.map.entity.InspectorLocation.inspectorId]) — never an Employee id.
 *
 * At most one row per inspector may have [endedAt] = null at a time ("one open shift") — enforced
 * at the DB level by a partial unique index (`ux_work_shifts_inspector_open` on
 * `(inspector_id) WHERE ended_at IS NULL`), bootstrapped at startup by
 * [uz.safecity.transportobserver.shifts.config.WorkShiftSchemaInitializer] since Hibernate's
 * `ddl-auto: update` cannot express a *partial* unique constraint through JPA annotations.
 * [uz.safecity.transportobserver.shifts.repository.WorkShiftRepository.startIfNoOpenShift] relies
 * on this index (`INSERT ... ON CONFLICT`) to make "start a shift" atomic — see that method's
 * kdoc; without the index, two concurrent start-shift calls for the same inspector could both
 * insert an open row.
 */
@Entity
@Table(name = "work_shifts")
class WorkShift(

	@Column(name = "inspector_id", nullable = false)
	var inspectorId: UUID,

	@Column(name = "started_at", nullable = false)
	var startedAt: Instant,

	@Column(name = "ended_at")
	var endedAt: Instant? = null

) : BaseEntity()

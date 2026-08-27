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
 *
 * [checkpointId] is the [uz.safecity.transportobserver.checkpoints.entity.Checkpoint] the
 * inspector declared they're posted at when they started this shift (`POST
 * /api/v1/inspector/me/shift/start` accepts it as an optional field) — a plain FK column, no
 * mapped JPA relation, same convention as [inspectorId]/every other "who/where" column in this
 * codebase. Nullable and OPTIONAL: not every inspector is tied to a single fixed checkpoint (e.g.
 * a roving inspector, or a client that hasn't been updated to send it yet), so omitting it must
 * remain valid — never backfilled or guessed. This is the FIRST place in the codebase that links
 * an inspector to a checkpoint at all (see the gap documented in
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService] /
 * [uz.safecity.transportobserver.map.dto.EmployeeLocationDto] kdocs); it only answers "which
 * checkpoint did they check into for *this* shift", not a permanent assignment.
 */
@Entity
@Table(name = "work_shifts")
class WorkShift(

	@Column(name = "inspector_id", nullable = false)
	var inspectorId: UUID,

	@Column(name = "started_at", nullable = false)
	var startedAt: Instant,

	@Column(name = "ended_at")
	var endedAt: Instant? = null,

	/** Optional checkpoint check-in for this shift — see class kdoc. */
	@Column(name = "checkpoint_id")
	var checkpointId: UUID? = null

) : BaseEntity()

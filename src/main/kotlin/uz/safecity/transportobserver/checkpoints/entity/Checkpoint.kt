package uz.safecity.transportobserver.checkpoints.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.locationtech.jts.geom.Point
import java.util.UUID

/**
 * A control/inspection checkpoint (TZ section 6, "Nazorat/transport" group).
 *
 * TODO (region module): [regionName] is a plain text field for now — same
 * reasoning as [uz.safecity.transportobserver.employees.entity.Employee.regionName]:
 * there is no real `regions` table yet. Migrate to `region_id` + a
 * `@ManyToOne Region` once that module lands.
 *
 * [location] uses PostGIS via Hibernate Spatial (`Point`, SRID 4326 / WGS84),
 * matching the existing convention for every other geo-bearing entity
 * ([uz.safecity.transportobserver.incidents.entity.Incident.location],
 * [uz.safecity.transportobserver.map.entity.VehicleLocation.location],
 * [uz.safecity.transportobserver.railsafe.entity.RailCrossingEvent.location]).
 *
 * [isActive] is the soft-deactivate flag (same "block" pattern as
 * [uz.safecity.transportobserver.auth.entity.Account.isActive] /
 * `EmployeeService.updateStatus`): checkpoints are never hard-deleted,
 * because a future `inspections` table (TZ section 6) is expected to carry a
 * `checkpoint_id` FK — deleting a row would either orphan that history or
 * force `ON DELETE CASCADE` and silently destroy inspection records instead.
 * `PATCH /api/v1/admin/checkpoints/{id}/status` toggles this; there is
 * deliberately no DELETE endpoint on this module.
 *
 * [checkpointTypeId] is the admin-managed replacement for the legacy free-text
 * [type] column: it points at [uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType]
 * (plain FK column, not a mapped JPA relation — same convention as
 * [uz.safecity.transportobserver.auth.entity.Account.employeeId]). [type] is kept
 * around, nullable and deprecated, purely for backward-compat with rows/clients
 * written before this column existed; new/updated checkpoints should populate
 * [checkpointTypeId] instead. Both are nullable so existing rows (created before
 * this migration) remain valid without a backfill.
 */
@Entity
@Table(name = "checkpoints")
class Checkpoint(

	@Column(nullable = false)
	var name: String,

	@Column(name = "region_name")
	var regionName: String? = null,

	@Column(columnDefinition = "geometry(Point,4326)", nullable = false)
	var location: Point,

	@Column(columnDefinition = "text")
	var description: String? = null,

	@Column(name = "is_active", nullable = false)
	var isActive: Boolean = true,

	/** @deprecated free-text legacy column — see [checkpointTypeId] kdoc above. Superseded by [checkpointTypeId]; kept for backward-compat, not written by new clients. */
	@Deprecated("Use checkpointTypeId (CheckpointType reference) instead.")
	@Column(length = 32)
	var type: String? = null,

	/** Admin-managed checkpoint category — FK to `checkpoint_types.id`. See class kdoc. */
	@Column(name = "checkpoint_type_id")
	var checkpointTypeId: UUID? = null

) : BaseEntity()

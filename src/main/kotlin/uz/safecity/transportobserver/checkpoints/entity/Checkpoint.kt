package uz.safecity.transportobserver.checkpoints.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.locationtech.jts.geom.Point

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

	/** Free-form for now, e.g. "post" / "mobile" — the TZ does not fix an enum for this. */
	@Column(length = 32)
	var type: String? = null

) : BaseEntity()

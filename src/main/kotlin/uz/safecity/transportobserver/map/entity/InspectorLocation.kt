package uz.safecity.transportobserver.map.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.locationtech.jts.geom.Point
import java.util.UUID

/**
 * Latest known position of an INSPECTOR (mobile app foreground-location heartbeat), one row per
 * inspector — same "latest-only, no history" MVP shape as
 * [uz.safecity.transportobserver.map.entity.VehicleLocation]: a new
 * `POST /api/v1/inspector/me/location` overwrites this row rather than inserting a new one (see
 * [uz.safecity.transportobserver.map.service.InspectorLocationService.upsertMyLocation]), so no
 * location history is retained — deliberate for this MVP phase, per task scope.
 *
 * [inspectorId] is an [uz.safecity.transportobserver.auth.entity.Account] id (not an
 * [uz.safecity.transportobserver.employees.entity.Employee] id), same convention as
 * [uz.safecity.transportobserver.inspections.entity.Inspection.assignedInspectorId] — enforced
 * unique so there is at most one row per inspector.
 *
 * "Online" status is NOT a persisted column: it is derived at read time from
 * [BaseEntity.updatedAt] (via `@LastModifiedDate`, already auto-managed on every save) — see
 * [uz.safecity.transportobserver.map.service.InspectorLocationService.listEmployeeLocations].
 */
@Entity
@Table(
	name = "inspector_locations",
	uniqueConstraints = [UniqueConstraint(name = "uq_inspector_locations_inspector_id", columnNames = ["inspector_id"])]
)
class InspectorLocation(

	@Column(name = "inspector_id", nullable = false)
	var inspectorId: UUID,

	@Column(columnDefinition = "geometry(Point,4326)", nullable = false)
	var location: Point

) : BaseEntity()

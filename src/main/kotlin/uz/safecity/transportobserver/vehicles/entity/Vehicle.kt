package uz.safecity.transportobserver.vehicles.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

enum class VehicleType { CAR, BUS, TRUCK, MOTORCYCLE, SPECIAL, OTHER }

/** Owner category — "jismoniy shaxs" (individual) vs "yuridik shaxs" (legal entity), as surfaced on the frontend's add-vehicle form. */
enum class VehicleOwnerType { INDIVIDUAL, LEGAL_ENTITY }

/** Risk level — "Past/O'rta/Yuqori" chip-select on the frontend's add/edit-vehicle form and registry list filter. */
enum class VehicleRiskLevel { LOW, MEDIUM, HIGH }

/**
 * Master registry row for a transport vehicle (TZ section 6, "Nazorat/transport"
 * group — `GET/POST /checkpoints, /vehicles`). Same shape/pattern as
 * [uz.safecity.transportobserver.checkpoints.entity.Checkpoint].
 *
 * Until this module existed, the only vehicle-related table was
 * [uz.safecity.transportobserver.map.entity.VehicleLocation] (live GPS pings),
 * whose `vehicleId: UUID` was never actually a foreign key to anything — there
 * was no registry to point at. This entity *is* that registry; going forward
 * `VehicleLocation.vehicleId` is expected to reference [Vehicle.id].
 *
 * TODO (region module): [regionName] is a plain text field for now — same
 * reasoning as [uz.safecity.transportobserver.employees.entity.Employee.regionName]
 * and [uz.safecity.transportobserver.checkpoints.entity.Checkpoint.regionName]:
 * there is no real `regions` table yet. Migrate to `region_id` + a
 * `@ManyToOne Region` once that module lands.
 *
 * [assignedEmployeeId] points at [uz.safecity.transportobserver.employees.entity.Employee.id]
 * — the *organizational* record of who this vehicle is assigned to (e.g. a
 * driver or a responsible employee on the staff roster), NOT an authentication
 * principal. Do not confuse this with
 * [uz.safecity.transportobserver.incidents.entity.Incident.assignedInspectorId],
 * which points at [uz.safecity.transportobserver.auth.entity.Account.id] (the
 * JWT principal an incident is routed to for scoping checks). A vehicle
 * assignment is plain HR/fleet bookkeeping and carries no auth/session
 * semantics — an assigned employee need not even have a login account.
 *
 * [isActive] is the soft-deactivate flag (same "block" pattern as
 * [uz.safecity.transportobserver.checkpoints.entity.Checkpoint.isActive]):
 * vehicles are never hard-deleted, because [uz.safecity.transportobserver.map.entity.VehicleLocation]
 * history (and, per the TZ, future incident/inspection records) may reference
 * this row — deleting it would either orphan that history or force
 * `ON DELETE CASCADE` and silently destroy it instead.
 * `PATCH /api/v1/admin/vehicles/{id}/status` toggles this; there is deliberately no
 * DELETE endpoint on this module.
 */
@Entity
@Table(name = "vehicles")
class Vehicle(

	@Column(name = "plate_number", nullable = false, unique = true, length = 20)
	var plateNumber: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var type: VehicleType,

	@Column
	var model: String? = null,

	// TODO (region module): text field until a real `regions` table exists — see kdoc above.
	@Column(name = "region_name")
	var regionName: String? = null,

	/** "Jismoniy shaxs" vs "yuridik shaxs" — nullable since older rows predate this field. */
	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", length = 32)
	var ownerType: VehicleOwnerType? = null,

	/** Employee.id — organizational assignment only, see class kdoc. */
	@Column(name = "assigned_employee_id")
	var assignedEmployeeId: UUID? = null,

	/** Xavf darajasi — mockup'dagi "Past/O'rta/Yuqori" chip-tanlov. Nullable — eski qatorlar uchun default aniqlanmagan, admin qo'lda belgilaydi (xuddi ownerType kabi). */
	@Enumerated(EnumType.STRING)
	@Column(name = "risk_level", length = 32)
	var riskLevel: VehicleRiskLevel? = null,

	@Column(name = "is_active", nullable = false)
	var isActive: Boolean = true

) : BaseEntity()

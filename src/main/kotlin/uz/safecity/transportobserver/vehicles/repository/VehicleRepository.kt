package uz.safecity.transportobserver.vehicles.repository

import uz.safecity.transportobserver.vehicles.entity.Vehicle
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface VehicleRepository : JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

	/** Case-insensitive uniqueness check for `plateNumber`, used on create/update. */
	fun existsByPlateNumberIgnoreCase(plateNumber: String): Boolean

	/** Same check, excluding the row being updated (self-match must not count as a conflict). */
	fun existsByPlateNumberIgnoreCaseAndIdNot(plateNumber: String, id: UUID): Boolean

	/** Backs potential `GET /api/v1/map/vehicles` enrichment (MapService) — only active vehicles belong on the map. */
	fun findByIsActiveTrue(): List<Vehicle>

	/**
	 * Reverse lookup for the mobile "Xodim kartasi" profile-detail screen's "Xizmat avtomobili"
	 * line ([uz.safecity.transportobserver.inspector.service.ProfileDetailService]) — the inverse
	 * direction of [uz.safecity.transportobserver.vehicles.entity.Vehicle.assignedEmployeeId] (see
	 * that field's kdoc). `First` + `IsActiveTrue` rather than returning every match: an employee
	 * is expected to have at most one currently-active assigned vehicle for this "my service
	 * vehicle" display, and a deactivated vehicle (see [Vehicle.isActive] kdoc) is no longer
	 * meaningfully "assigned" for display purposes even if the FK column is untouched.
	 */
	fun findFirstByAssignedEmployeeIdAndIsActiveTrue(assignedEmployeeId: UUID): Vehicle?
}

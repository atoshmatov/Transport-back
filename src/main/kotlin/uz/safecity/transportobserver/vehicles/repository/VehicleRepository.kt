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
}

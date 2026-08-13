package uz.safecity.transportobserver.map.repository

import uz.safecity.transportobserver.map.entity.VehicleLocation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VehicleLocationRepository : JpaRepository<VehicleLocation, UUID> {
	fun findByVehicleId(vehicleId: UUID): VehicleLocation?
}

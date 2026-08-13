package uz.safecity.transportobserver.map.dto

import uz.safecity.transportobserver.map.entity.VehicleLocation
import uz.safecity.transportobserver.vehicles.entity.Vehicle
import uz.safecity.transportobserver.vehicles.entity.VehicleType
import java.time.Instant
import java.util.UUID

/**
 * [plateNumber]/[vehicleType] are enriched from the [Vehicle] registry
 * (uz.safecity.transportobserver.vehicles) by matching [vehicleId] against
 * [Vehicle.id] — see [uz.safecity.transportobserver.map.service.MapService.listLatestLocations].
 * Both are nullable: a [VehicleLocation] row whose `vehicleId` doesn't (yet)
 * match any registry row is still returned, just without vehicle details,
 * rather than being dropped from the map.
 */
data class VehicleLocationDto(
	val vehicleId: UUID,
	val latitude: Double,
	val longitude: Double,
	val speedKmh: Double?,
	val recordedAt: Instant,
	val plateNumber: String?,
	val vehicleType: VehicleType?
) {
	companion object {
		fun from(entity: VehicleLocation, vehicle: Vehicle?) = VehicleLocationDto(
			vehicleId = entity.vehicleId,
			latitude = entity.location.y,
			longitude = entity.location.x,
			speedKmh = entity.speedKmh,
			recordedAt = entity.recordedAt,
			plateNumber = vehicle?.plateNumber,
			vehicleType = vehicle?.type
		)
	}
}

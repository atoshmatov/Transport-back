package uz.safecity.transportobserver.map.service

import uz.safecity.transportobserver.map.dto.VehicleLocationDto
import uz.safecity.transportobserver.map.repository.VehicleLocationRepository
import uz.safecity.transportobserver.vehicles.repository.VehicleRepository
import org.springframework.stereotype.Service

/**
 * Skeleton only. TODO (next phase): push live updates over STOMP
 * (/topic/map/vehicles) as locations come in, geofencing/railsafe overlap
 * checks, and a bounding-box query for the visible map viewport instead of
 * `findAll()`.
 */
@Service
class MapService(
	private val vehicleLocationRepository: VehicleLocationRepository,
	private val vehicleRepository: VehicleRepository
) {

	/**
	 * Enriches each live location with its [uz.safecity.transportobserver.vehicles.entity.Vehicle]
	 * registry row (plateNumber/type) via a single batched lookup — see
	 * [uz.safecity.transportobserver.map.dto.VehicleLocationDto] kdoc.
	 */
	fun listLatestLocations(): List<VehicleLocationDto> {
		val locations = vehicleLocationRepository.findAll()
		val vehiclesById = vehicleRepository.findAllById(locations.map { it.vehicleId }).associateBy { it.id }
		return locations.map { VehicleLocationDto.from(it, vehiclesById[it.vehicleId]) }
	}
}

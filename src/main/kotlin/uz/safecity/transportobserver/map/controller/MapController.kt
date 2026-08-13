package uz.safecity.transportobserver.map.controller

import uz.safecity.transportobserver.checkpoints.dto.CheckpointDto
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.map.dto.VehicleLocationDto
import uz.safecity.transportobserver.map.service.MapService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `/checkpoints` here is deliberately unguarded by `@PreAuthorize` (open to
 * every authenticated role, same as `/vehicles`) — INSPECTOR needs to see
 * active checkpoints on their own map, per TZ's Map/Geo group. This differs
 * from `/api/v1/checkpoints` ([uz.safecity.transportobserver.checkpoints.controller.CheckpointController]),
 * which is the Admin-section CRUD and is INSPECTOR-restricted there. Only
 * active checkpoints are returned — see [CheckpointService.listActiveForMap].
 */
@RestController
@RequestMapping("/api/v1/map")
class MapController(
	private val mapService: MapService,
	private val checkpointService: CheckpointService
) {

	@GetMapping("/vehicles")
	fun listVehicleLocations(): ResponseEntity<ApiResponse<List<VehicleLocationDto>>> =
		ResponseEntity.ok(ApiResponse.ok(mapService.listLatestLocations()))

	@GetMapping("/checkpoints")
	fun listCheckpoints(): ResponseEntity<ApiResponse<List<CheckpointDto>>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.listActiveForMap()))
}

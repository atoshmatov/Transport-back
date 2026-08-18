package uz.safecity.transportobserver.map.controller

import uz.safecity.transportobserver.checkpoints.dto.CheckpointDto
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.map.dto.EmployeeLocationDto
import uz.safecity.transportobserver.map.dto.VehicleLocationDto
import uz.safecity.transportobserver.map.service.InspectorLocationService
import uz.safecity.transportobserver.map.service.MapService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
 *
 * `/employees` IS restricted (ADMIN/OPERATOR/SUPER_ADMIN only) — unlike vehicle/checkpoint
 * positions, an inspector's live location is not something every role should see on the map.
 */
@RestController
@RequestMapping("/api/v1/map")
class MapController(
	private val mapService: MapService,
	private val checkpointService: CheckpointService,
	private val inspectorLocationService: InspectorLocationService
) {

	@GetMapping("/vehicles")
	fun listVehicleLocations(): ResponseEntity<ApiResponse<List<VehicleLocationDto>>> =
		ResponseEntity.ok(ApiResponse.ok(mapService.listLatestLocations()))

	@GetMapping("/checkpoints")
	fun listCheckpoints(): ResponseEntity<ApiResponse<List<CheckpointDto>>> =
		ResponseEntity.ok(ApiResponse.ok(checkpointService.listActiveForMap()))

	/** Admin/Operator map view — see [InspectorLocationService.listEmployeeLocations] kdoc, incl. the `category` caveat. */
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
	@GetMapping("/employees")
	fun listEmployeeLocations(): ResponseEntity<ApiResponse<List<EmployeeLocationDto>>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorLocationService.listEmployeeLocations()))
}

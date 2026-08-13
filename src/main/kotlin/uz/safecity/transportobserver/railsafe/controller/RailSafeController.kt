package uz.safecity.transportobserver.railsafe.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.railsafe.entity.RailCrossingEvent
import uz.safecity.transportobserver.railsafe.service.RailSafeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/railsafe")
class RailSafeController(
	private val railSafeService: RailSafeService
) {

	@GetMapping("/events")
	fun list(): ResponseEntity<ApiResponse<List<RailCrossingEvent>>> =
		ResponseEntity.ok(ApiResponse.ok(railSafeService.list()))

	@GetMapping("/events/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<RailCrossingEvent>> =
		ResponseEntity.ok(ApiResponse.ok(railSafeService.getById(id)))
}

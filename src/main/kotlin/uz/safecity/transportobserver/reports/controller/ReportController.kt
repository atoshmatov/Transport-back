package uz.safecity.transportobserver.reports.controller

import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.service.ReportService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/reports")
class ReportController(
	private val reportService: ReportService
) {

	@GetMapping
	fun list(): ResponseEntity<ApiResponse<List<Report>>> =
		ResponseEntity.ok(ApiResponse.ok(reportService.list()))

	@GetMapping("/{id}")
	fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<Report>> =
		ResponseEntity.ok(ApiResponse.ok(reportService.getById(id)))
}

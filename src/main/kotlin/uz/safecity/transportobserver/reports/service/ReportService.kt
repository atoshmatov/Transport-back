package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.reports.entity.Report
import uz.safecity.transportobserver.reports.repository.ReportRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Skeleton only. TODO (next phase): async generation job (probably via
 * RabbitMQ), MinIO upload, and download-URL signing.
 */
@Service
class ReportService(
	private val reportRepository: ReportRepository
) {

	fun list(): List<Report> = reportRepository.findAll()

	fun getById(id: UUID): Report =
		reportRepository.findById(id).orElseThrow { ResourceNotFoundException("Hisobot topilmadi: $id") }
}

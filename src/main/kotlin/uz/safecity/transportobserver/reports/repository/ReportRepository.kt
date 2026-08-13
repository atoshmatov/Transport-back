package uz.safecity.transportobserver.reports.repository

import uz.safecity.transportobserver.reports.entity.Report
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReportRepository : JpaRepository<Report, UUID>

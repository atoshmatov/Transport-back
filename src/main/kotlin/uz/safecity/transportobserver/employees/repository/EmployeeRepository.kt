package uz.safecity.transportobserver.employees.repository

import uz.safecity.transportobserver.common.dto.RegionCountProjection
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.entity.EmployeeStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface EmployeeRepository : JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

	/** Reports `regions-distribution` screen (ReportService) — see [RegionCountProjection] kdoc. */
	@Query("select e.regionName as regionName, count(e) as cnt from Employee e group by e.regionName")
	fun countGroupByRegion(): List<RegionCountProjection>

	/**
	 * Reports dashboard `totalEmployeesCount` (ReportStatsService) — every Employee row that is
	 * still current staff, i.e. everything except [EmployeeStatus.DISMISSED]. DISMISSED is
	 * deliberately excluded: it marks someone who no longer works here, so counting them into
	 * "jami xodimlar" (total employees) would overstate current headcount. ACTIVE and INACTIVE
	 * both count here (INACTIVE = e.g. on leave, still staff) — that is a different axis from
	 * [uz.safecity.transportobserver.auth.repository.AccountRepository.countByIsActiveTrueAndEmployeeIdIsNotNull]
	 * (login-account enabled/disabled), which is what the card's "Faol: X" subtext uses.
	 */
	fun countByStatusNot(status: EmployeeStatus): Long
}

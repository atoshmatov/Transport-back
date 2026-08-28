package uz.safecity.transportobserver.employees.repository

import uz.safecity.transportobserver.employees.entity.EmployeePositionHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmployeePositionHistoryRepository : JpaRepository<EmployeePositionHistory, UUID> {

	/** The employee's currently-open spell (if any) — see [EmployeePositionHistory] kdoc re: at most one at a time. */
	fun findByEmployeeIdAndEndedAtIsNull(employeeId: UUID): EmployeePositionHistory?

	/** Full history for `GET /api/v1/admin/employees/{id}/position-history`, newest spell first. */
	fun findByEmployeeIdOrderByStartedAtDesc(employeeId: UUID): List<EmployeePositionHistory>
}

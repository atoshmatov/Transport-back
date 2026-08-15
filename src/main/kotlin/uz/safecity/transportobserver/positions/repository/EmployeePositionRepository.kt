package uz.safecity.transportobserver.positions.repository

import uz.safecity.transportobserver.positions.entity.EmployeePosition
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmployeePositionRepository : JpaRepository<EmployeePosition, UUID> {
	fun existsByName(name: String): Boolean
}

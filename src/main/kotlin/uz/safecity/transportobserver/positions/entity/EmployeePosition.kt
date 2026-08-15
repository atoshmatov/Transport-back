package uz.safecity.transportobserver.positions.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "employee_positions")
class EmployeePosition(
	@Column(nullable = false, unique = true)
	var name: String,

	@Column(columnDefinition = "text")
	var description: String? = null
) : BaseEntity()

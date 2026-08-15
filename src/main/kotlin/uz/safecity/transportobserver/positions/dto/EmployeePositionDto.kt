package uz.safecity.transportobserver.positions.dto

import uz.safecity.transportobserver.positions.entity.EmployeePosition
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class EmployeePositionDto(
	val id: UUID,
	val name: String,
	val description: String?
) {
	companion object {
		fun from(position: EmployeePosition) = EmployeePositionDto(
			id = requireNotNull(position.id),
			name = position.name,
			description = position.description
		)
	}
}

data class CreateEmployeePositionRequest(
	@field:NotBlank(message = "Lavozim nomi bo'sh bo'lishi mumkin emas")
	val name: String,
	val description: String? = null
)

data class UpdateEmployeePositionRequest(
	@field:NotBlank(message = "Lavozim nomi bo'sh bo'lishi mumkin emas")
	val name: String,
	val description: String? = null
)

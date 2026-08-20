package uz.safecity.transportobserver.checkpointtypes.dto

import uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class CheckpointTypeDto(
	val id: UUID,
	val name: String,
	val description: String?
) {
	companion object {
		fun from(checkpointType: CheckpointType) = CheckpointTypeDto(
			id = requireNotNull(checkpointType.id),
			name = checkpointType.name,
			description = checkpointType.description
		)
	}
}

data class CreateCheckpointTypeRequest(
	@field:NotBlank(message = "Nazorat punkti turi nomi bo'sh bo'lishi mumkin emas")
	val name: String,
	val description: String? = null
)

data class UpdateCheckpointTypeRequest(
	@field:NotBlank(message = "Nazorat punkti turi nomi bo'sh bo'lishi mumkin emas")
	val name: String,
	val description: String? = null
)

package uz.safecity.transportobserver.checkpoints.dto

import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Shared response shape for both the Admin CRUD (CheckpointController) and the map view (MapController). */
data class CheckpointDto(
	val id: UUID,
	val name: String,
	val regionName: String?,
	val latitude: Double,
	val longitude: Double,
	val description: String?,
	val isActive: Boolean,
	val type: String?,
	val updatedAt: Instant?
) {
	companion object {
		fun from(checkpoint: Checkpoint) = CheckpointDto(
			id = requireNotNull(checkpoint.id),
			name = checkpoint.name,
			regionName = checkpoint.regionName,
			latitude = checkpoint.location.y,
			longitude = checkpoint.location.x,
			description = checkpoint.description,
			isActive = checkpoint.isActive,
			type = checkpoint.type,
			updatedAt = checkpoint.updatedAt
		)
	}
}

data class CreateCheckpointRequest(
	@field:NotBlank(message = "Nomi majburiy")
	@field:Size(max = 150, message = "Nomi 150 belgidan oshmasligi kerak")
	val name: String,

	// TODO (region module): text field until a real `regions` table exists — see Checkpoint kdoc.
	val regionName: String? = null,

	@field:NotNull(message = "latitude majburiy")
	val latitude: Double?,

	@field:NotNull(message = "longitude majburiy")
	val longitude: Double?,

	val description: String? = null,
	val type: String? = null
)

/** isActive is intentionally excluded — changed only via `PATCH /{id}/status`. */
data class UpdateCheckpointRequest(
	@field:NotBlank(message = "Nomi majburiy")
	@field:Size(max = 150, message = "Nomi 150 belgidan oshmasligi kerak")
	val name: String,

	val regionName: String? = null,

	@field:NotNull(message = "latitude majburiy")
	val latitude: Double?,

	@field:NotNull(message = "longitude majburiy")
	val longitude: Double?,

	val description: String? = null,
	val type: String? = null
)

data class UpdateCheckpointStatusRequest(
	@field:NotNull(message = "isActive majburiy")
	val isActive: Boolean? = null
)

package uz.safecity.transportobserver.checkpoints.dto

import uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType
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
	/** @deprecated legacy free-text category — see [Checkpoint.type] kdoc. Prefer [checkpointTypeId]/[checkpointTypeName]. */
	@Deprecated("Use checkpointTypeId/checkpointTypeName instead.")
	val type: String?,
	val checkpointTypeId: UUID?,
	val checkpointTypeName: String?,
	val updatedAt: Instant?
) {
	companion object {
		/**
		 * [checkpointType] is the already-resolved [CheckpointType] row for
		 * [Checkpoint.checkpointTypeId] (or `null` if unset/not found) — callers
		 * batch-fetch it (single/list) so this stays a pure mapping function with
		 * no repository access of its own. See [uz.safecity.transportobserver.checkpoints.service.CheckpointService].
		 */
		@Suppress("DEPRECATION")
		fun from(checkpoint: Checkpoint, checkpointType: CheckpointType? = null) = CheckpointDto(
			id = requireNotNull(checkpoint.id),
			name = checkpoint.name,
			regionName = checkpoint.regionName,
			latitude = checkpoint.location.y,
			longitude = checkpoint.location.x,
			description = checkpoint.description,
			isActive = checkpoint.isActive,
			type = checkpoint.type,
			checkpointTypeId = checkpoint.checkpointTypeId,
			checkpointTypeName = checkpointType?.name,
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

	/** @deprecated legacy free-text category, ignored when [checkpointTypeId] is set — see [Checkpoint.type] kdoc. */
	@Deprecated("Use checkpointTypeId instead.")
	val type: String? = null,

	/** Admin-selected [uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType] id — the replacement for [type]. */
	val checkpointTypeId: UUID? = null
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

	/** @deprecated legacy free-text category, ignored when [checkpointTypeId] is set — see [Checkpoint.type] kdoc. */
	@Deprecated("Use checkpointTypeId instead.")
	val type: String? = null,

	/** Admin-selected [uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType] id — the replacement for [type]. */
	val checkpointTypeId: UUID? = null
)

data class UpdateCheckpointStatusRequest(
	@field:NotNull(message = "isActive majburiy")
	val isActive: Boolean? = null
)

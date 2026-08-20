package uz.safecity.transportobserver.vehicles.dto

import uz.safecity.transportobserver.vehicles.entity.Vehicle
import uz.safecity.transportobserver.vehicles.entity.VehicleOwnerType
import uz.safecity.transportobserver.vehicles.entity.VehicleType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Shared response shape for both the Admin CRUD (VehicleController) and, potentially, the map view (MapController). */
data class VehicleDto(
	val id: UUID,
	val plateNumber: String,
	val type: VehicleType,
	val model: String?,
	val regionName: String?,
	val ownerType: VehicleOwnerType?,
	val assignedEmployeeId: UUID?,
	val isActive: Boolean,
	val updatedAt: Instant?
) {
	companion object {
		fun from(vehicle: Vehicle) = VehicleDto(
			id = requireNotNull(vehicle.id),
			plateNumber = vehicle.plateNumber,
			type = vehicle.type,
			model = vehicle.model,
			regionName = vehicle.regionName,
			ownerType = vehicle.ownerType,
			assignedEmployeeId = vehicle.assignedEmployeeId,
			isActive = vehicle.isActive,
			updatedAt = vehicle.updatedAt
		)
	}
}

data class CreateVehicleRequest(
	@field:NotBlank(message = "Davlat raqami majburiy")
	@field:Size(max = 20, message = "Davlat raqami 20 belgidan oshmasligi kerak")
	val plateNumber: String,

	@field:NotNull(message = "type majburiy")
	val type: VehicleType?,

	val model: String? = null,

	// TODO (region module): text field until a real `regions` table exists — see Vehicle kdoc.
	val regionName: String? = null,

	/** "Jismoniy shaxs" (INDIVIDUAL) vs "yuridik shaxs" (LEGAL_ENTITY); nullable/optional. */
	val ownerType: VehicleOwnerType? = null,

	/** Employee.id — see Vehicle kdoc re: not Account.id. */
	val assignedEmployeeId: UUID? = null
)

/** isActive is intentionally excluded — changed only via `PATCH /{id}/status`. */
data class UpdateVehicleRequest(
	@field:NotBlank(message = "Davlat raqami majburiy")
	@field:Size(max = 20, message = "Davlat raqami 20 belgidan oshmasligi kerak")
	val plateNumber: String,

	@field:NotNull(message = "type majburiy")
	val type: VehicleType?,

	val model: String? = null,
	val regionName: String? = null,
	val ownerType: VehicleOwnerType? = null,
	val assignedEmployeeId: UUID? = null
)

data class UpdateVehicleStatusRequest(
	@field:NotNull(message = "isActive majburiy")
	val isActive: Boolean? = null
)

package uz.safecity.transportobserver.vehicles.dto

import uz.safecity.transportobserver.vehicles.entity.Vehicle
import uz.safecity.transportobserver.vehicles.entity.VehicleOwnerType
import uz.safecity.transportobserver.vehicles.entity.VehicleRiskLevel
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
	val riskLevel: VehicleRiskLevel?,
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
			riskLevel = vehicle.riskLevel,
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
	val assignedEmployeeId: UUID? = null,

	/** "Past/O'rta/Yuqori" chip-tanlov; nullable/optional — see Vehicle kdoc. */
	val riskLevel: VehicleRiskLevel? = null
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
	val assignedEmployeeId: UUID? = null,
	val riskLevel: VehicleRiskLevel? = null
)

data class UpdateVehicleStatusRequest(
	@field:NotNull(message = "isActive majburiy")
	val isActive: Boolean? = null
)

/**
 * Lightweight projection for `GET /api/v1/inspector/vehicles` (mobile "hodisa qayd etish"
 * transport picker) — see [uz.safecity.transportobserver.inspector.controller.InspectorPanelController.listVehiclesForPicker]
 * kdoc for why this is a separate, INSPECTOR-facing endpoint rather than an extension of
 * [uz.safecity.transportobserver.vehicles.controller.VehicleController.list].
 *
 * Deliberately only these 4 fields: [ownerType], [assignedEmployeeId], [regionName] and
 * [isActive]/[VehicleDto.updatedAt] are admin/fleet bookkeeping the inspector has no reason to
 * see when merely picking a vehicle to attach to an incident report. Only active vehicles are
 * ever mapped to this DTO in the first place (see [uz.safecity.transportobserver.vehicles.service.VehicleService.listForInspectorPicker]),
 * so there is no `isActive` field to expose here at all.
 */
data class VehiclePickerDto(
	val id: UUID,
	val plateNumber: String,
	val model: String?,
	val type: VehicleType
) {
	companion object {
		fun from(vehicle: Vehicle) = VehiclePickerDto(
			id = requireNotNull(vehicle.id),
			plateNumber = vehicle.plateNumber,
			model = vehicle.model,
			type = vehicle.type
		)
	}
}

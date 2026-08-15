package uz.safecity.transportobserver.regions.dto

import uz.safecity.transportobserver.regions.entity.Region
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class RegionDto(
	val id: UUID,
	val name: String,
	val code: String?
) {
	companion object {
		fun from(region: Region) = RegionDto(
			id = requireNotNull(region.id),
			name = region.name,
			code = region.code
		)
	}
}

data class CreateRegionRequest(
	@field:NotBlank(message = "Hudud nomi bo'sh bo'lishi mumkin emas")
	val name: String,
	val code: String? = null
)

data class UpdateRegionRequest(
	@field:NotBlank(message = "Hudud nomi bo'sh bo'lishi mumkin emas")
	val name: String,
	val code: String? = null
)

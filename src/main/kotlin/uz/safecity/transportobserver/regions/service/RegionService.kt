package uz.safecity.transportobserver.regions.service

import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.regions.dto.CreateRegionRequest
import uz.safecity.transportobserver.regions.dto.RegionDto
import uz.safecity.transportobserver.regions.dto.UpdateRegionRequest
import uz.safecity.transportobserver.regions.entity.Region
import uz.safecity.transportobserver.regions.repository.RegionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RegionService(
	private val regionRepository: RegionRepository
) {

	fun listAll(): List<RegionDto> {
		return regionRepository.findAll().map { RegionDto.from(it) }
	}

	fun getById(id: UUID): RegionDto {
		val region = regionRepository.findById(id)
			.orElseThrow { ResourceNotFoundException("Hudud topilmadi: $id") }
		return RegionDto.from(region)
	}

	@Transactional
	fun create(request: CreateRegionRequest): RegionDto {
		val trimmedName = request.name.trim()
		if (regionRepository.existsByName(trimmedName)) {
			throw ConflictException("Hudud allaqachon mavjud: $trimmedName")
		}
		val region = regionRepository.save(
			Region(
				name = trimmedName,
				code = request.code?.trim()?.takeIf { it.isNotEmpty() }
			)
		)
		return RegionDto.from(region)
	}

	@Transactional
	fun update(id: UUID, request: UpdateRegionRequest): RegionDto {
		val region = regionRepository.findById(id)
			.orElseThrow { ResourceNotFoundException("Hudud topilmadi: $id") }

		val trimmedName = request.name.trim()
		if (region.name != trimmedName && regionRepository.existsByName(trimmedName)) {
			throw ConflictException("Hudud allaqachon mavjud: $trimmedName")
		}

		region.name = trimmedName
		region.code = request.code?.trim()?.takeIf { it.isNotEmpty() }
		val saved = regionRepository.save(region)
		return RegionDto.from(saved)
	}

	@Transactional
	fun delete(id: UUID) {
		val region = regionRepository.findById(id)
			.orElseThrow { ResourceNotFoundException("Hudud topilmadi: $id") }
		regionRepository.delete(region)
	}
}

package uz.safecity.transportobserver.positions.service

import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.positions.dto.CreateEmployeePositionRequest
import uz.safecity.transportobserver.positions.dto.EmployeePositionDto
import uz.safecity.transportobserver.positions.dto.UpdateEmployeePositionRequest
import uz.safecity.transportobserver.positions.entity.EmployeePosition
import uz.safecity.transportobserver.positions.repository.EmployeePositionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EmployeePositionService(
	private val employeePositionRepository: EmployeePositionRepository
) {

	fun listAll(): List<EmployeePositionDto> {
		return employeePositionRepository.findAll().map { EmployeePositionDto.from(it) }
	}

	fun getById(id: UUID): EmployeePositionDto {
		val position = employeePositionRepository.findById(id)
			.orElseThrow { ResourceNotFoundException("Lavozim topilmadi: $id") }
		return EmployeePositionDto.from(position)
	}

	@Transactional
	fun create(request: CreateEmployeePositionRequest): EmployeePositionDto {
		val trimmedName = request.name.trim()
		if (employeePositionRepository.existsByName(trimmedName)) {
			throw ConflictException("Lavozim allaqachon mavjud: $trimmedName")
		}
		val position = employeePositionRepository.save(
			EmployeePosition(
				name = trimmedName,
				description = request.description?.trim()?.takeIf { it.isNotEmpty() }
			)
		)
		return EmployeePositionDto.from(position)
	}

	@Transactional
	fun update(id: UUID, request: UpdateEmployeePositionRequest): EmployeePositionDto {
		val position = employeePositionRepository.findById(id)
			.orElseThrow { ResourceNotFoundException("Lavozim topilmadi: $id") }

		val trimmedName = request.name.trim()
		if (position.name != trimmedName && employeePositionRepository.existsByName(trimmedName)) {
			throw ConflictException("Lavozim allaqachon mavjud: $trimmedName")
		}

		position.name = trimmedName
		position.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
		val saved = employeePositionRepository.save(position)
		return EmployeePositionDto.from(saved)
	}

	@Transactional
	fun delete(id: UUID) {
		val position = employeePositionRepository.findById(id)
			.orElseThrow { ResourceNotFoundException("Lavozim topilmadi: $id") }
		employeePositionRepository.delete(position)
	}
}

package uz.safecity.transportobserver.checkpointtypes.service

import uz.safecity.transportobserver.checkpointtypes.dto.CheckpointTypeDto
import uz.safecity.transportobserver.checkpointtypes.dto.CreateCheckpointTypeRequest
import uz.safecity.transportobserver.checkpointtypes.dto.UpdateCheckpointTypeRequest
import uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType
import uz.safecity.transportobserver.checkpointtypes.repository.CheckpointTypeRepository
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CheckpointTypeService(
	private val checkpointTypeRepository: CheckpointTypeRepository
) {

	fun listAll(): List<CheckpointTypeDto> =
		checkpointTypeRepository.findAll().map { CheckpointTypeDto.from(it) }

	fun getById(id: UUID): CheckpointTypeDto = CheckpointTypeDto.from(findOrThrow(id))

	@Transactional
	fun create(request: CreateCheckpointTypeRequest): CheckpointTypeDto {
		val trimmedName = request.name.trim()
		if (checkpointTypeRepository.existsByName(trimmedName)) {
			throw ConflictException("error.checkpoint-type.already-exists", trimmedName)
		}
		val checkpointType = checkpointTypeRepository.save(
			CheckpointType(
				name = trimmedName,
				description = request.description?.trim()?.takeIf { it.isNotEmpty() }
			)
		)
		return CheckpointTypeDto.from(checkpointType)
	}

	@Transactional
	fun update(id: UUID, request: UpdateCheckpointTypeRequest): CheckpointTypeDto {
		val checkpointType = findOrThrow(id)

		val trimmedName = request.name.trim()
		if (checkpointType.name != trimmedName && checkpointTypeRepository.existsByName(trimmedName)) {
			throw ConflictException("error.checkpoint-type.already-exists", trimmedName)
		}

		checkpointType.name = trimmedName
		checkpointType.description = request.description?.trim()?.takeIf { it.isNotEmpty() }
		val saved = checkpointTypeRepository.save(checkpointType)
		return CheckpointTypeDto.from(saved)
	}

	@Transactional
	fun delete(id: UUID) {
		val checkpointType = findOrThrow(id)
		checkpointTypeRepository.delete(checkpointType)
	}

	/** Consumed by [uz.safecity.transportobserver.checkpoints.service.CheckpointService] to validate/enrich checkpoints. */
	fun findEntityOrThrow(id: UUID): CheckpointType = findOrThrow(id)

	/** Batched lookup for list enrichment — see [CheckpointTypeRepository.findByIdIn] kdoc. */
	fun findEntitiesByIdIn(ids: Collection<UUID>): List<CheckpointType> =
		if (ids.isEmpty()) emptyList() else checkpointTypeRepository.findByIdIn(ids)

	private fun findOrThrow(id: UUID): CheckpointType =
		checkpointTypeRepository.findById(id)
			.orElseThrow { ResourceNotFoundException("error.checkpoint-type.not-found", id) }
}

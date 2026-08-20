package uz.safecity.transportobserver.checkpointtypes.repository

import uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CheckpointTypeRepository : JpaRepository<CheckpointType, UUID> {
	fun existsByName(name: String): Boolean

	/** Batched lookup for [uz.safecity.transportobserver.checkpoints.dto.CheckpointDto] enrichment — avoids N+1 when listing checkpoints. */
	fun findByIdIn(ids: Collection<UUID>): List<CheckpointType>
}

package uz.safecity.transportobserver.railsafe.service

import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.railsafe.entity.RailCrossingEvent
import uz.safecity.transportobserver.railsafe.repository.RailCrossingEventRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Skeleton only. TODO (next phase): RabbitMQ consumer for sensor/CV events,
 * CRITICAL-severity -> notifications module trigger, per-crossing history.
 */
@Service
class RailSafeService(
	private val railCrossingEventRepository: RailCrossingEventRepository
) {

	fun list(): List<RailCrossingEvent> = railCrossingEventRepository.findAll()

	fun getById(id: UUID): RailCrossingEvent =
		railCrossingEventRepository.findById(id).orElseThrow { ResourceNotFoundException("Hodisa topilmadi: $id") }
}

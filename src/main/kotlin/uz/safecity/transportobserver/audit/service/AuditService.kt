package uz.safecity.transportobserver.audit.service

import uz.safecity.transportobserver.audit.entity.AuditLog
import uz.safecity.transportobserver.audit.repository.AuditLogRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Skeleton only. TODO (next phase): hook into AuthService (login
 * success/failure, password reset, account lock) and other modules'
 * mutating actions; consider an AOP aspect instead of manual calls.
 */
@Service
class AuditService(
	private val auditLogRepository: AuditLogRepository
) {

	fun record(actorAccountId: UUID?, action: String, entityType: String? = null, entityId: UUID? = null, metadata: String? = null) {
		auditLogRepository.save(
			AuditLog(
				actorAccountId = actorAccountId,
				action = action,
				entityType = entityType,
				entityId = entityId,
				metadata = metadata
			)
		)
	}

	fun list(): List<AuditLog> = auditLogRepository.findAll()
}

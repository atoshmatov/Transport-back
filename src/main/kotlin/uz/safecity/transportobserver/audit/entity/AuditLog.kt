package uz.safecity.transportobserver.audit.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * Append-only trail of security/business-relevant actions (login, password
 * reset, account block, incident status change, ...). `metadata` is a JSON
 * blob kept as text for now — no fixed shape yet, per-action producers
 * decide what to log.
 */
@Entity
@Table(name = "audit_logs")
class AuditLog(

	@Column(name = "actor_account_id")
	var actorAccountId: UUID?,

	@Column(nullable = false, length = 128)
	var action: String,

	@Column(name = "entity_type", length = 64)
	var entityType: String? = null,

	@Column(name = "entity_id")
	var entityId: UUID? = null,

	@Column(columnDefinition = "text")
	var metadata: String? = null

) : BaseEntity()

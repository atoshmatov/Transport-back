package uz.safecity.transportobserver.audit.repository

import uz.safecity.transportobserver.audit.entity.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID>

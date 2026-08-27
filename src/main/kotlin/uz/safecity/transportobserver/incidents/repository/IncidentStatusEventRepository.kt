package uz.safecity.transportobserver.incidents.repository

import uz.safecity.transportobserver.incidents.entity.IncidentStatusEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Append-only status-history trail for [uz.safecity.transportobserver.incidents.entity.Incident]
 * — see [IncidentStatusEvent] kdoc.
 */
interface IncidentStatusEventRepository : JpaRepository<IncidentStatusEvent, UUID> {

	/** Mobile/web "Hodisa kartasi" timeline — see IncidentService#toDetailDto. */
	fun findByIncidentIdOrderByOccurredAtAsc(incidentId: UUID): List<IncidentStatusEvent>
}

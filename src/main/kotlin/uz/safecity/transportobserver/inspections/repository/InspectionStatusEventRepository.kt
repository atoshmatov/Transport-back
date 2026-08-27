package uz.safecity.transportobserver.inspections.repository

import uz.safecity.transportobserver.inspections.entity.InspectionStatusEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Append-only JARAYON timeline for [uz.safecity.transportobserver.inspections.entity.Inspection]
 * — see [InspectionStatusEvent] kdoc. Mirrors
 * [uz.safecity.transportobserver.incidents.repository.IncidentStatusEventRepository].
 */
interface InspectionStatusEventRepository : JpaRepository<InspectionStatusEvent, UUID> {

	/** Mobile/web "Tekshiruv hisoboti" -> "JARAYON" section — see InspectionDetailDto#from. */
	fun findByInspectionIdOrderByOccurredAtAsc(inspectionId: UUID): List<InspectionStatusEvent>
}

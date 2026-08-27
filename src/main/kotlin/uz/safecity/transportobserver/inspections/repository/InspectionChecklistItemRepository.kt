package uz.safecity.transportobserver.inspections.repository

import uz.safecity.transportobserver.inspections.entity.InspectionChecklistItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Checklist rows for [uz.safecity.transportobserver.inspections.entity.Inspection] — see
 * [InspectionChecklistItem] kdoc for the "mobile submits the full list on completion, this table
 * just stores it" contract.
 */
interface InspectionChecklistItemRepository : JpaRepository<InspectionChecklistItem, UUID> {

	/** Mobile/web "Tekshiruv hisoboti" -> "BANDLAR NATIJASI" section — see InspectionDetailDto#from. */
	fun findByInspectionIdOrderByOrderIndexAsc(inspectionId: UUID): List<InspectionChecklistItem>

	/** Wholesale replace-on-completion — see [InspectionChecklistItem] kdoc for why this isn't a diff/upsert. */
	fun deleteByInspectionId(inspectionId: UUID)
}

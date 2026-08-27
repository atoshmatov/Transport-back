package uz.safecity.transportobserver.inspections.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One row per meaningful milestone in an [Inspection]'s life — backs the mobile "Tekshiruv
 * hisoboti" JARAYON timeline (`TO-Screen.dc.html` `reportDetail`: "Tekshiruv boshlandi -> Bandlar
 * to'ldirildi -> Imzolar olindi -> Markazga yuborildi"). Append-only (never updated/deleted),
 * mirroring [uz.safecity.transportobserver.incidents.entity.IncidentStatusEvent] exactly, minus
 * that sibling's [uz.safecity.transportobserver.incidents.entity.IncidentStatus]/actor columns —
 * an Inspection's timeline is a fixed 4-step happy path with no branching cases to disambiguate,
 * so a bare label + timestamp is enough; see
 * [uz.safecity.transportobserver.inspections.service.InspectionService]#updateStatus for exactly
 * where each of the 4 labels gets written.
 */
@Entity
@Table(name = "inspection_status_events")
class InspectionStatusEvent(

	@Column(name = "inspection_id", nullable = false)
	var inspectionId: UUID,

	/** One of "Tekshiruv boshlandi" / "Bandlar to'ldirildi" / "Imzolar olindi" / "Markazga yuborildi" — see class kdoc. */
	@Column(nullable = false, length = 128)
	var label: String,

	@Column(name = "occurred_at", nullable = false)
	var occurredAt: Instant = Instant.now()

) : BaseEntity()

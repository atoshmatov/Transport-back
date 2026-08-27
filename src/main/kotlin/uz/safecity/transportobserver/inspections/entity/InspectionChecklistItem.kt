package uz.safecity.transportobserver.inspections.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.util.UUID

/**
 * "Yaroqli"/"Kamchilik" per design's `TO-Screen.dc.html` `reportDetail` config ("BANDLAR
 * NATIJASI"). [NOT_APPLICABLE] has no counterpart in the design's two-state chip but exists so a
 * band that genuinely doesn't apply to a given checkpoint/vehicle isn't forced into a misleading
 * PASS/DEFICIENT bucket.
 */
enum class ChecklistResult { PASS, DEFICIENT, NOT_APPLICABLE }

/**
 * One checklist row ("BANDLAR NATIJASI" in the design) belonging to one [Inspection]. There is
 * deliberately no separate "checklist template" table: per the ENG ODDIY contract this module
 * settled on (see [uz.safecity.transportobserver.inspections.service.InspectionService]#updateStatus
 * kdoc), the inspector's mobile app is the source of truth for band *names* — it submits the full
 * label+result(+note) list on `PATCH /inspections/{id}/status` when completing, and this table
 * only ever stores what was submitted, never invents or looks up labels server-side.
 *
 * [inspectionId] is a plain FK column (not a mapped `@ManyToOne`) — same convention as
 * [Inspection.checkpointId] / [Inspection.assignedInspectorId].
 *
 * Rows are replaced wholesale (delete-then-insert) on every completion submission rather than
 * diffed/upserted — an inspection is completed exactly once in the normal flow, so there is no
 * meaningful "edit an existing checklist" case to optimize for yet.
 */
@Entity
@Table(name = "inspection_checklist_items")
class InspectionChecklistItem(

	@Column(name = "inspection_id", nullable = false)
	var inspectionId: UUID,

	/** Band name, e.g. "Haydovchi hujjatlari" — submitted by the mobile client, see class kdoc. */
	@Column(nullable = false, columnDefinition = "text")
	var label: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var result: ChecklistResult,

	/** "ANIQLANGAN KAMCHILIKLAR" description, e.g. "4 o'rindiqda ishlamaydi" — only meaningful when [result] is [ChecklistResult.DEFICIENT], but not enforced null/non-null server-side (see kdoc on [uz.safecity.transportobserver.inspections.dto.ChecklistItemRequest]). */
	@Column(name = "deficiency_note", columnDefinition = "text")
	var deficiencyNote: String? = null,

	/** Display order within the checklist — mirrors the order the mobile client submitted the items in. */
	@Column(name = "order_index", nullable = false)
	var orderIndex: Int = 0

) : BaseEntity()

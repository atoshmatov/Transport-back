package uz.safecity.transportobserver.inspections.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.inspections.dto.ChecklistItemRequest
import uz.safecity.transportobserver.inspections.entity.ChecklistResult
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionChecklistItemRepository
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.inspections.repository.InspectionStatusEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the "Tekshiruv hisoboti" (reportDetail) backend contract added for the new mobile
 * checklist/signature/timeline screen: [uz.safecity.transportobserver.inspections.entity.InspectionChecklistItem]
 * saved wholesale on completion, [Inspection.inspectorSignedAt]/[Inspection.driverSignedAt]
 * timestamp-confirmation semantics (NOT cryptographic signatures — see their kdoc), and the
 * [uz.safecity.transportobserver.inspections.entity.InspectionStatusEvent] JARAYON timeline
 * written by [InspectionService.updateStatus].
 */
@SpringBootTest
@Transactional
class InspectionServiceTests {

	@Autowired
	lateinit var inspectionService: InspectionService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var inspectionRepository: InspectionRepository

	@Autowired
	lateinit var checklistItemRepository: InspectionChecklistItemRepository

	@Autowired
	lateinit var statusEventRepository: InspectionStatusEventRepository

	private fun createInspectorAccount(): Account {
		val account = Account(
			username = "insp_${UUID.randomUUID().toString().take(20)}",
			passwordHash = "irrelevant-for-this-test",
			role = RoleType.INSPECTOR,
			mustChangePassword = false,
			isActive = true
		)
		return accountRepository.save(account)
	}

	/** Bare [Inspection] row, bypassing InspectionService#create — no real Checkpoint row is needed since [InspectionService.getById]/[InspectionService.updateStatus] both tolerate a dangling checkpointId (see InspectionDto kdoc). */
	private fun createInspection(assignedInspectorId: UUID, status: InspectionStatus = InspectionStatus.PLANNED): Inspection =
		inspectionRepository.save(
			Inspection(
				checkpointId = UUID.randomUUID(),
				assignedInspectorId = assignedInspectorId,
				status = status
			)
		)

	@Test
	fun `starting an inspection writes a single 'Tekshiruv boshlandi' timeline event`() {
		val inspector = createInspectorAccount()
		val inspection = createInspection(requireNotNull(inspector.id))

		inspectionService.updateStatus(
			requireNotNull(inspection.id),
			InspectionStatus.IN_PROGRESS,
			notes = null,
			actorAccountId = inspector.id,
			actorRole = RoleType.INSPECTOR
		)

		val history = statusEventRepository.findByInspectionIdOrderByOccurredAtAsc(requireNotNull(inspection.id))
		assertEquals(1, history.size)
		assertEquals("Tekshiruv boshlandi", history.single().label)
	}

	@Test
	fun `completing with a checklist and driver confirmation persists checklist rows, both signatures, and the full JARAYON timeline`() {
		val inspector = createInspectorAccount()
		val inspection = createInspection(requireNotNull(inspector.id), status = InspectionStatus.IN_PROGRESS)

		val dto = inspectionService.updateStatus(
			requireNotNull(inspection.id),
			InspectionStatus.COMPLETED,
			notes = "Yakunlandi",
			actorAccountId = inspector.id,
			actorRole = RoleType.INSPECTOR,
			checklistItems = listOf(
				ChecklistItemRequest(label = "Haydovchi hujjatlari", result = ChecklistResult.PASS),
				ChecklistItemRequest(label = "O'rindiqlar", result = ChecklistResult.DEFICIENT, deficiencyNote = "4 o'rindiqda ishlamaydi")
			),
			driverConfirmed = true,
			witnessName = "Karimov Sardor"
		)

		assertEquals(InspectionStatus.COMPLETED, dto.status)
		assertNotNull(dto.performedAt)

		val items = checklistItemRepository.findByInspectionIdOrderByOrderIndexAsc(requireNotNull(inspection.id))
		assertEquals(2, items.size)
		assertEquals("Haydovchi hujjatlari", items[0].label)
		assertEquals(ChecklistResult.PASS, items[0].result)
		assertNull(items[0].deficiencyNote)
		assertEquals("O'rindiqlar", items[1].label)
		assertEquals(ChecklistResult.DEFICIENT, items[1].result)
		assertEquals("4 o'rindiqda ishlamaydi", items[1].deficiencyNote)

		val saved = inspectionRepository.findById(requireNotNull(inspection.id)).orElseThrow()
		assertNotNull(saved.inspectorSignedAt, "Inspector's own completion call must timestamp-confirm automatically")
		assertNotNull(saved.driverSignedAt, "driverConfirmed=true must set driverSignedAt")
		assertEquals("Karimov Sardor", saved.witnessName)

		val history = statusEventRepository.findByInspectionIdOrderByOccurredAtAsc(requireNotNull(inspection.id))
			.map { it.label }
		assertEquals(listOf("Bandlar to'ldirildi", "Imzolar olindi", "Markazga yuborildi"), history)
	}

	@Test
	fun `completing without a checklist or driver confirmation still auto-signs the inspector and always logs 'Markazga yuborildi'`() {
		val inspector = createInspectorAccount()
		val inspection = createInspection(requireNotNull(inspector.id), status = InspectionStatus.IN_PROGRESS)

		inspectionService.updateStatus(
			requireNotNull(inspection.id),
			InspectionStatus.COMPLETED,
			notes = null,
			actorAccountId = inspector.id,
			actorRole = RoleType.INSPECTOR
		)

		assertTrue(checklistItemRepository.findByInspectionIdOrderByOrderIndexAsc(requireNotNull(inspection.id)).isEmpty())

		val saved = inspectionRepository.findById(requireNotNull(inspection.id)).orElseThrow()
		assertNotNull(saved.inspectorSignedAt)
		assertNull(saved.driverSignedAt, "driverConfirmed defaults to false — must not be set")

		val history = statusEventRepository.findByInspectionIdOrderByOccurredAtAsc(requireNotNull(inspection.id))
			.map { it.label }
		// No "Bandlar to'ldirildi" (no checklist submitted), but the inspector's own auto-signature
		// still counts as "Imzolar olindi", and completion always logs "Markazga yuborildi".
		assertEquals(listOf("Imzolar olindi", "Markazga yuborildi"), history)
	}

	@Test
	fun `getById exposes checklist items, signature timestamps and the JARAYON timeline together`() {
		val inspector = createInspectorAccount()
		val inspection = createInspection(requireNotNull(inspector.id), status = InspectionStatus.IN_PROGRESS)

		inspectionService.updateStatus(
			requireNotNull(inspection.id),
			InspectionStatus.COMPLETED,
			notes = null,
			actorAccountId = inspector.id,
			actorRole = RoleType.INSPECTOR,
			checklistItems = listOf(ChecklistItemRequest(label = "Yorug'lik tizimi", result = ChecklistResult.PASS)),
			driverConfirmed = true
		)

		val detail = inspectionService.getById(
			requireNotNull(inspection.id),
			uz.safecity.transportobserver.auth.security.CustomUserDetails.from(inspector)
		)

		assertEquals(1, detail.checklistItems.size)
		assertEquals("Yorug'lik tizimi", detail.checklistItems.single().label)
		assertNotNull(detail.inspectorSignedAt)
		assertNotNull(detail.driverSignedAt)
		assertEquals(listOf("Bandlar to'ldirildi", "Imzolar olindi", "Markazga yuborildi"), detail.statusHistory.map { it.label })
	}
}

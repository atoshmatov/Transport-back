package uz.safecity.transportobserver

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import uz.safecity.transportobserver.checkpoints.repository.CheckpointRepository
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.util.GeoUtils
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.incidents.service.IncidentService
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.inspections.service.InspectionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the status-transition state machine added to [IncidentService.updateStatus] /
 * [InspectionService.updateStatus] — before this, any status could move to any other status
 * (e.g. a resolved incident could be silently reopened all the way back to NEW), which
 * corrupts anything that assumes a status only moves forward through the workflow (SLA/
 * reporting metrics, the mobile "yakunlandi" flow, etc).
 */
@SpringBootTest
@Transactional
class StatusTransitionTests {

	@Autowired
	lateinit var incidentService: IncidentService

	@Autowired
	lateinit var incidentRepository: IncidentRepository

	@Autowired
	lateinit var inspectionService: InspectionService

	@Autowired
	lateinit var inspectionRepository: InspectionRepository

	@Autowired
	lateinit var checkpointRepository: CheckpointRepository

	@Autowired
	lateinit var accountRepository: AccountRepository

	private fun createAccount(role: RoleType): Account =
		accountRepository.save(
			Account(
				username = "user_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = role,
				mustChangePassword = false,
				isActive = true
			)
		)

	private fun createIncident(assignedInspectorId: UUID?, status: IncidentStatus): Incident =
		incidentRepository.save(
			Incident(
				title = "Test incident",
				type = IncidentType.OTHER,
				status = status,
				assignedInspectorId = assignedInspectorId
			)
		)

	private fun createCheckpoint(): Checkpoint =
		checkpointRepository.save(
			Checkpoint(
				name = "Test checkpoint",
				location = GeoUtils.toPoint(41.3, 69.2)
			)
		)

	private fun createInspection(assignedInspectorId: UUID?, status: InspectionStatus): Inspection =
		inspectionRepository.save(
			Inspection(
				checkpointId = requireNotNull(createCheckpoint().id),
				assignedInspectorId = assignedInspectorId,
				status = status
			)
		)

	// --- Incident: valid transitions ---

	@Test
	fun `incident NEW to IN_PROGRESS is allowed for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val incident = createIncident(assignedInspectorId = null, status = IncidentStatus.NEW)

		val result = incidentService.updateStatus(
			requireNotNull(incident.id), IncidentStatus.IN_PROGRESS, operator.id, RoleType.OPERATOR
		)

		assertEquals(IncidentStatus.IN_PROGRESS, result.status)
	}

	@Test
	fun `incident RESOLVED can reopen to IN_PROGRESS for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val incident = createIncident(assignedInspectorId = null, status = IncidentStatus.RESOLVED)

		val result = incidentService.updateStatus(
			requireNotNull(incident.id), IncidentStatus.IN_PROGRESS, operator.id, RoleType.OPERATOR
		)

		assertEquals(IncidentStatus.IN_PROGRESS, result.status)
	}

	@Test
	fun `incident REJECTED can reopen to IN_PROGRESS for INSPECTOR on own incident`() {
		val inspector = createAccount(RoleType.INSPECTOR)
		val incident = createIncident(assignedInspectorId = inspector.id, status = IncidentStatus.REJECTED)

		val result = incidentService.updateStatus(
			requireNotNull(incident.id), IncidentStatus.IN_PROGRESS, inspector.id, RoleType.INSPECTOR
		)

		assertEquals(IncidentStatus.IN_PROGRESS, result.status)
	}

	// --- Incident: invalid transitions ---

	@Test
	fun `incident RESOLVED to NEW is rejected for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val incident = createIncident(assignedInspectorId = null, status = IncidentStatus.RESOLVED)

		val ex = assertThrows(BadRequestException::class.java) {
			incidentService.updateStatus(requireNotNull(incident.id), IncidentStatus.NEW, operator.id, RoleType.OPERATOR)
		}
		assertEquals("error.incident.invalid-status-transition", ex.messageKey)

		// Status must remain unchanged in the DB.
		val reloaded = incidentRepository.findById(requireNotNull(incident.id)).orElseThrow()
		assertEquals(IncidentStatus.RESOLVED, reloaded.status)
	}

	@Test
	fun `incident NEW directly to RESOLVED is rejected for INSPECTOR`() {
		val inspector = createAccount(RoleType.INSPECTOR)
		val incident = createIncident(assignedInspectorId = inspector.id, status = IncidentStatus.NEW)

		assertThrows(BadRequestException::class.java) {
			incidentService.updateStatus(requireNotNull(incident.id), IncidentStatus.RESOLVED, inspector.id, RoleType.INSPECTOR)
		}
	}

	@Test
	fun `incident IN_PROGRESS to NEW is rejected for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val incident = createIncident(assignedInspectorId = null, status = IncidentStatus.IN_PROGRESS)

		assertThrows(BadRequestException::class.java) {
			incidentService.updateStatus(requireNotNull(incident.id), IncidentStatus.NEW, operator.id, RoleType.OPERATOR)
		}
	}

	// --- Incident: ADMIN/SUPER_ADMIN bypass ---

	@Test
	fun `incident RESOLVED to NEW is allowed for ADMIN (bypass)`() {
		val admin = createAccount(RoleType.ADMIN)
		val incident = createIncident(assignedInspectorId = null, status = IncidentStatus.RESOLVED)

		val result = incidentService.updateStatus(requireNotNull(incident.id), IncidentStatus.NEW, admin.id, RoleType.ADMIN)

		assertEquals(IncidentStatus.NEW, result.status)
	}

	@Test
	fun `incident RESOLVED to NEW is allowed for SUPER_ADMIN (bypass)`() {
		val superAdmin = createAccount(RoleType.SUPER_ADMIN)
		val incident = createIncident(assignedInspectorId = null, status = IncidentStatus.RESOLVED)

		val result = incidentService.updateStatus(requireNotNull(incident.id), IncidentStatus.NEW, superAdmin.id, RoleType.SUPER_ADMIN)

		assertEquals(IncidentStatus.NEW, result.status)
	}

	// --- Inspection: valid transitions ---

	@Test
	fun `inspection PLANNED to IN_PROGRESS is allowed for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val inspection = createInspection(assignedInspectorId = null, status = InspectionStatus.PLANNED)

		val result = inspectionService.updateStatus(
			requireNotNull(inspection.id), InspectionStatus.IN_PROGRESS, null, operator.id, RoleType.OPERATOR
		)

		assertEquals(InspectionStatus.IN_PROGRESS, result.status)
	}

	@Test
	fun `inspection IN_PROGRESS to COMPLETED is allowed for INSPECTOR on own inspection`() {
		val inspector = createAccount(RoleType.INSPECTOR)
		val inspection = createInspection(assignedInspectorId = inspector.id, status = InspectionStatus.IN_PROGRESS)

		val result = inspectionService.updateStatus(
			requireNotNull(inspection.id), InspectionStatus.COMPLETED, null, inspector.id, RoleType.INSPECTOR
		)

		assertEquals(InspectionStatus.COMPLETED, result.status)
	}

	@Test
	fun `inspection PLANNED to CANCELLED is allowed (wildcard target) for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val inspection = createInspection(assignedInspectorId = null, status = InspectionStatus.PLANNED)

		val result = inspectionService.updateStatus(
			requireNotNull(inspection.id), InspectionStatus.CANCELLED, null, operator.id, RoleType.OPERATOR
		)

		assertEquals(InspectionStatus.CANCELLED, result.status)
	}

	@Test
	fun `inspection COMPLETED to CANCELLED is allowed (wildcard target) for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val inspection = createInspection(assignedInspectorId = null, status = InspectionStatus.COMPLETED)

		val result = inspectionService.updateStatus(
			requireNotNull(inspection.id), InspectionStatus.CANCELLED, null, operator.id, RoleType.OPERATOR
		)

		assertEquals(InspectionStatus.CANCELLED, result.status)
	}

	// --- Inspection: invalid transitions ---

	@Test
	fun `inspection PLANNED directly to COMPLETED is rejected for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val inspection = createInspection(assignedInspectorId = null, status = InspectionStatus.PLANNED)

		assertThrows(BadRequestException::class.java) {
			inspectionService.updateStatus(requireNotNull(inspection.id), InspectionStatus.COMPLETED, null, operator.id, RoleType.OPERATOR)
		}
	}

	@Test
	fun `inspection COMPLETED back to IN_PROGRESS is rejected for INSPECTOR`() {
		val inspector = createAccount(RoleType.INSPECTOR)
		val inspection = createInspection(assignedInspectorId = inspector.id, status = InspectionStatus.COMPLETED)

		assertThrows(BadRequestException::class.java) {
			inspectionService.updateStatus(requireNotNull(inspection.id), InspectionStatus.IN_PROGRESS, null, inspector.id, RoleType.INSPECTOR)
		}
	}

	@Test
	fun `inspection CANCELLED to IN_PROGRESS is rejected for OPERATOR`() {
		val operator = createAccount(RoleType.OPERATOR)
		val inspection = createInspection(assignedInspectorId = null, status = InspectionStatus.CANCELLED)

		assertThrows(BadRequestException::class.java) {
			inspectionService.updateStatus(requireNotNull(inspection.id), InspectionStatus.IN_PROGRESS, null, operator.id, RoleType.OPERATOR)
		}
	}

	// --- Inspection: ADMIN bypass ---

	@Test
	fun `inspection COMPLETED back to PLANNED is allowed for ADMIN (bypass)`() {
		val admin = createAccount(RoleType.ADMIN)
		val inspection = createInspection(assignedInspectorId = null, status = InspectionStatus.COMPLETED)

		val result = inspectionService.updateStatus(
			requireNotNull(inspection.id), InspectionStatus.PLANNED, null, admin.id, RoleType.ADMIN
		)

		assertEquals(InspectionStatus.PLANNED, result.status)
	}

	// --- Same-status no-op is allowed for every role (idempotent retry) ---

	@Test
	fun `incident same-status update is a no-op, not an invalid transition`() {
		val operator = createAccount(RoleType.OPERATOR)
		val incident = createIncident(assignedInspectorId = null, status = IncidentStatus.IN_PROGRESS)

		val result = incidentService.updateStatus(
			requireNotNull(incident.id), IncidentStatus.IN_PROGRESS, operator.id, RoleType.OPERATOR
		)

		assertEquals(IncidentStatus.IN_PROGRESS, result.status)
	}
}

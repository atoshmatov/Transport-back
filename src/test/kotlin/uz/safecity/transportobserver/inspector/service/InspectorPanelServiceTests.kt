package uz.safecity.transportobserver.inspector.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.incidents.entity.ActionType
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the MEDIUM gap flagged by kotlin-reviewer QA on the inspector-statistics
 * endpoints (InspectorPanelController/Service): no test previously verified
 * (1) that one inspector's `me/...` stats never leak another inspector's Incident
 * rows (IDOR/scoping via `principal.accountId`), and (2) that `Incident.actionType`
 * being null (legacy rows, see [Incident] kdoc) is filtered out cleanly instead of
 * NPE'ing or being miscounted in `action-type-breakdown`.
 */
@SpringBootTest
@Transactional
class InspectorPanelServiceTests {

	@Autowired
	lateinit var inspectorPanelService: InspectorPanelService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var incidentRepository: IncidentRepository

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

	private fun createIncident(
		assignedInspectorId: UUID,
		type: IncidentType,
		actionType: ActionType?
	): Incident {
		val incident = Incident(
			title = "Test incident",
			type = type,
			actionType = actionType,
			assignedInspectorId = assignedInspectorId
		)
		return incidentRepository.save(incident)
	}

	@Test
	fun `getActionTypeBreakdown only counts the authenticated inspector's own incidents`() {
		val inspectorA = createInspectorAccount()
		val inspectorB = createInspectorAccount()

		// Inspector A: 1 VIOLATION_RECORDED + 1 WARNING_GIVEN.
		createIncident(requireNotNull(inspectorA.id), IncidentType.ACCIDENT, ActionType.VIOLATION_RECORDED)
		createIncident(requireNotNull(inspectorA.id), IncidentType.VIOLATION, ActionType.WARNING_GIVEN)

		// Inspector B: 3 VIOLATION_RECORDED incidents that must NEVER show up in A's breakdown.
		repeat(3) {
			createIncident(requireNotNull(inspectorB.id), IncidentType.ACCIDENT, ActionType.VIOLATION_RECORDED)
		}

		val principalA = CustomUserDetails.from(inspectorA)
		val breakdownForA = inspectorPanelService.getActionTypeBreakdown(principalA)
			.associate { it.actionType to it.count }

		assertEquals(1L, breakdownForA[ActionType.VIOLATION_RECORDED], "Should only see A's own VIOLATION_RECORDED incident, not B's 3")
		assertEquals(1L, breakdownForA[ActionType.WARNING_GIVEN])
		assertEquals(0L, breakdownForA[ActionType.PHOTO_EVIDENCE_SUBMITTED])
		assertEquals(0L, breakdownForA[ActionType.DANGER_REPORTED])

		// Sanity: same call for B sees only B's own 3 incidents, not A's.
		val principalB = CustomUserDetails.from(inspectorB)
		val breakdownForB = inspectorPanelService.getActionTypeBreakdown(principalB)
			.associate { it.actionType to it.count }
		assertEquals(3L, breakdownForB[ActionType.VIOLATION_RECORDED])
		assertEquals(0L, breakdownForB[ActionType.WARNING_GIVEN])
	}

	@Test
	fun `getMyStats action counts do not leak another inspector's incidents`() {
		val inspectorA = createInspectorAccount()
		val inspectorB = createInspectorAccount()

		createIncident(requireNotNull(inspectorA.id), IncidentType.SECURITY, ActionType.DANGER_REPORTED)
		createIncident(requireNotNull(inspectorB.id), IncidentType.SECURITY, ActionType.DANGER_REPORTED)
		createIncident(requireNotNull(inspectorB.id), IncidentType.SECURITY, ActionType.DANGER_REPORTED)

		val statsForA = inspectorPanelService.getMyStats(CustomUserDetails.from(inspectorA))

		assertEquals(1, statsForA.dangersReportedCount, "Inspector A must only see their own DANGER_REPORTED incident, not B's 2 extra")
	}

	@Test
	fun `getActionTypeBreakdown excludes legacy incidents with a null actionType instead of throwing or miscounting`() {
		val inspector = createInspectorAccount()

		createIncident(requireNotNull(inspector.id), IncidentType.OTHER, ActionType.PHOTO_EVIDENCE_SUBMITTED)
		// Legacy row predating the actionType field (see Incident kdoc) — must not NPE and must not
		// be folded into any bucket.
		createIncident(requireNotNull(inspector.id), IncidentType.OTHER, actionType = null)
		createIncident(requireNotNull(inspector.id), IncidentType.OTHER, actionType = null)

		val principal = CustomUserDetails.from(inspector)

		// No try/catch needed: if this throws (e.g. NPE on the null actionType), JUnit fails the
		// test with that exception directly — that alone proves the "must not NPE" requirement.
		val breakdown = inspectorPanelService.getActionTypeBreakdown(principal)
		val byAction = breakdown.associate { it.actionType to it.count }

		assertEquals(1L, byAction[ActionType.PHOTO_EVIDENCE_SUBMITTED])
		val totalCounted = breakdown.sumOf { it.count }
		assertEquals(1L, totalCounted, "The 2 null-actionType incidents must be excluded entirely, not counted anywhere")

		// getMyStats reuses the same grouped query — must also stay NPE-free with null actionType rows present.
		val stats = inspectorPanelService.getMyStats(principal)
		assertEquals(1, stats.photoEvidenceSubmittedCount)
	}

	@Test
	fun `getIncidentTypeBreakdown always returns one row per IncidentType, zero-filled when absent`() {
		val inspector = createInspectorAccount()
		createIncident(requireNotNull(inspector.id), IncidentType.ACCIDENT, ActionType.VIOLATION_RECORDED)

		val breakdown = inspectorPanelService.getIncidentTypeBreakdown(CustomUserDetails.from(inspector))

		assertEquals(IncidentType.entries.size, breakdown.size)
		val byType = breakdown.associate { it.type to it.count }
		assertEquals(1L, byType[IncidentType.ACCIDENT])
		assertEquals(0L, byType[IncidentType.VIOLATION])
		assertEquals(0L, byType[IncidentType.TECHNICAL_FAULT])
		assertEquals(0L, byType[IncidentType.SECURITY])
		assertEquals(0L, byType[IncidentType.OTHER])
	}
}

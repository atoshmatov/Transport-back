package uz.safecity.transportobserver.checkpoints.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.shifts.service.WorkShiftService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Covers the new checkpoint-detail endpoints backing the mobile "Nazorat punkti" screen — see
 * [CheckpointStatsService] kdoc. In particular:
 * - "on-duty" must reflect ONLY currently-open [uz.safecity.transportobserver.shifts.entity.WorkShift]
 *   rows checked into the target checkpoint (never a closed shift, never a shift checked into a
 *   different checkpoint).
 * - "today-stats" counts must be scoped to the target checkpoint only, and the two fields this
 *   codebase cannot honestly compute ([uz.safecity.transportobserver.checkpoints.dto.CheckpointTodayStatsDto.detectedIncidentsCount]/
 *   [uz.safecity.transportobserver.checkpoints.dto.CheckpointTodayStatsDto.averageInspectionDurationMinutes])
 *   must always come back `null`, never a fabricated `0`.
 */
@SpringBootTest
@Transactional
class CheckpointStatsServiceTests {

	@Autowired
	lateinit var checkpointStatsService: CheckpointStatsService

	@Autowired
	lateinit var checkpointService: CheckpointService

	@Autowired
	lateinit var workShiftService: WorkShiftService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var employeeRepository: EmployeeRepository

	@Autowired
	lateinit var inspectionRepository: InspectionRepository

	@Autowired
	lateinit var incidentRepository: IncidentRepository

	private fun createCheckpoint(name: String = "Test nazorat punkti ${UUID.randomUUID()}") =
		checkpointService.create(CreateCheckpointRequest(name = name, latitude = 41.3, longitude = 69.2))

	private fun createInspector(fullName: String, position: String? = null): Account {
		val employee = employeeRepository.save(Employee(fullName = fullName, position = position))
		return accountRepository.save(
			Account(
				username = "insp_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.INSPECTOR,
				employeeId = requireNotNull(employee.id),
				mustChangePassword = false,
				isActive = true
			)
		)
	}

	private fun inspectorPrincipal(account: Account): CustomUserDetails = CustomUserDetails(
		accountId = requireNotNull(account.id),
		role = RoleType.INSPECTOR,
		username = account.username,
		passwordHash = account.passwordHash,
		authorities = listOf(SimpleGrantedAuthority(RoleType.INSPECTOR.authority)),
		mustChangePassword = false,
		enabled = true,
		accountNonLocked = true
	)

	@Test
	fun `getOnDuty returns only inspectors with an open shift checked into this checkpoint`() {
		val checkpoint = createCheckpoint()
		val otherCheckpoint = createCheckpoint()
		val onDutyHere = createInspector("Aliyev Vali", position = "Katta inspektor")
		val onDutyElsewhere = createInspector("Karimov Bek")
		val notOnDuty = createInspector("Yusupov Aziz")

		workShiftService.startShift(inspectorPrincipal(onDutyHere), checkpoint.id)
		workShiftService.startShift(inspectorPrincipal(onDutyElsewhere), otherCheckpoint.id)
		// notOnDuty never starts a shift at all.

		val roster = checkpointStatsService.getOnDuty(checkpoint.id)

		assertEquals(1, roster.size)
		val row = roster.single()
		assertEquals(onDutyHere.id, row.inspectorId)
		assertEquals("Aliyev Vali", row.fullName)
		assertEquals("Katta inspektor", row.position)
		// Fresh test account, never touched a session/GPS signal — must show offline, not fabricated true.
		assertFalse(row.online)
	}

	@Test
	fun `getOnDuty excludes a closed shift`() {
		val checkpoint = createCheckpoint()
		val inspector = createInspector("Ended Shift Inspector")
		val principal = inspectorPrincipal(inspector)

		workShiftService.startShift(principal, checkpoint.id)
		workShiftService.endShift(principal)

		assertTrue(checkpointStatsService.getOnDuty(checkpoint.id).isEmpty())
	}

	@Test
	fun `getOnDuty on an unknown checkpoint throws ResourceNotFoundException`() {
		assertThrows(ResourceNotFoundException::class.java) {
			checkpointStatsService.getOnDuty(UUID.randomUUID())
		}
	}

	@Test
	fun `getTodayStats counts only this checkpoint's inspections completed today and never fabricates the unknowable fields`() {
		val checkpoint = createCheckpoint()
		val otherCheckpoint = createCheckpoint()

		// Completed today, at this checkpoint -> counted.
		inspectionRepository.save(
			Inspection(checkpointId = checkpoint.id, status = InspectionStatus.COMPLETED, performedAt = Instant.now())
		)
		// Completed today, at a DIFFERENT checkpoint -> must not leak into this checkpoint's count.
		inspectionRepository.save(
			Inspection(checkpointId = otherCheckpoint.id, status = InspectionStatus.COMPLETED, performedAt = Instant.now())
		)
		// Same checkpoint but PLANNED (not completed) -> must not be counted.
		inspectionRepository.save(Inspection(checkpointId = checkpoint.id, status = InspectionStatus.PLANNED))

		// Incident explicitly linked (user-confirmed) to this checkpoint -> counted.
		incidentRepository.save(Incident(title = "Linked here", type = IncidentType.VIOLATION, checkpointId = checkpoint.id))
		// Incident linked to a DIFFERENT checkpoint -> must not leak into this checkpoint's count.
		incidentRepository.save(Incident(title = "Linked elsewhere", type = IncidentType.VIOLATION, checkpointId = otherCheckpoint.id))
		// Incident with no checkpointId at all (never fabricated/guessed) -> must not be counted anywhere.
		incidentRepository.save(Incident(title = "Unlinked", type = IncidentType.VIOLATION))

		val stats = checkpointStatsService.getTodayStats(checkpoint.id)

		assertEquals(checkpoint.id, stats.checkpointId)
		assertEquals(1, stats.inspectionsCompletedTodayCount)
		assertEquals(0, stats.onDutyInspectorsCount)
		assertEquals(1, stats.detectedIncidentsCount, "only the incident explicitly linked to THIS checkpoint counts")
		assertNull(stats.averageInspectionDurationMinutes, "no honest 'check duration' field exists — must stay null")
	}

	@Test
	fun `getMetrics counts this month's completed inspections and distinct inspectors scoped to this checkpoint`() {
		val checkpoint = createCheckpoint()
		val inspectorA = createInspector("Metrics Inspector A")
		val inspectorB = createInspector("Metrics Inspector B")

		inspectionRepository.save(
			Inspection(
				checkpointId = checkpoint.id,
				assignedInspectorId = inspectorA.id,
				status = InspectionStatus.COMPLETED,
				performedAt = Instant.now()
			)
		)
		inspectionRepository.save(
			Inspection(
				checkpointId = checkpoint.id,
				assignedInspectorId = inspectorB.id,
				status = InspectionStatus.PLANNED
			)
		)

		// Incident explicitly linked to this checkpoint, created this month -> counted.
		incidentRepository.save(Incident(title = "This month, this checkpoint", type = IncidentType.VIOLATION, checkpointId = checkpoint.id))

		val metrics = checkpointStatsService.getMetrics(checkpoint.id)

		assertEquals(checkpoint.id, metrics.checkpointId)
		assertEquals(1, metrics.inspectionsThisMonthCount)
		assertEquals(2, metrics.inspectorsThisMonthCount)
		assertEquals(1, metrics.detectedCasesCount, "only the incident explicitly linked to THIS checkpoint counts")
	}

	@Test
	fun `getTodayStats and getMetrics on an unknown checkpoint throw ResourceNotFoundException`() {
		assertThrows(ResourceNotFoundException::class.java) {
			checkpointStatsService.getTodayStats(UUID.randomUUID())
		}
		assertThrows(ResourceNotFoundException::class.java) {
			checkpointStatsService.getMetrics(UUID.randomUUID())
		}
	}
}

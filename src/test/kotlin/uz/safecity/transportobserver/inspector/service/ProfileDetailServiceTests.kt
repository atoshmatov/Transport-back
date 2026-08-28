package uz.safecity.transportobserver.inspector.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.employees.service.EmployeePositionHistoryService
import uz.safecity.transportobserver.incidents.entity.ActionType
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.shifts.entity.WorkShift
import uz.safecity.transportobserver.shifts.repository.WorkShiftRepository
import uz.safecity.transportobserver.vehicles.entity.Vehicle
import uz.safecity.transportobserver.vehicles.entity.VehicleOwnerType
import uz.safecity.transportobserver.vehicles.entity.VehicleType
import uz.safecity.transportobserver.vehicles.repository.VehicleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Covers `GET /api/v1/inspector/me/profile-detail` (mobile "Xodim kartasi" / `profileDetail`
 * screen), backed by [ProfileDetailService]. Follows the same service-level testing convention as
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelServiceTests] (direct
 * [CustomUserDetails] construction rather than MockMvc): this codebase has no established way to
 * hand MockMvc's `@WithMockUser` a real [CustomUserDetails] principal for `@AuthenticationPrincipal`
 * binding yet, so the `@PreAuthorize`-driven 403 is instead verified at this service's own
 * defense-in-depth check ([ProfileDetailService.assertInspector]), which mirrors the controller
 * annotation exactly (same pattern as [InspectorPanelService.assertInspector]).
 */
@SpringBootTest
@Transactional
class ProfileDetailServiceTests {

	@Autowired
	lateinit var profileDetailService: ProfileDetailService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var employeeRepository: EmployeeRepository

	@Autowired
	lateinit var vehicleRepository: VehicleRepository

	@Autowired
	lateinit var incidentRepository: IncidentRepository

	@Autowired
	lateinit var inspectionRepository: InspectionRepository

	@Autowired
	lateinit var workShiftRepository: WorkShiftRepository

	@Autowired
	lateinit var employeePositionHistoryService: EmployeePositionHistoryService

	private fun createEmployee(): Employee = employeeRepository.save(
		Employee(
			fullName = "Akbarov Sardor",
			position = "Katta inspektor",
			department = "Yo'l nazorati bo'limi",
			regionName = "Toshkent shahri",
			phoneNumber = "+998901234567",
			hiredAt = LocalDate.of(2023, 1, 14),
			photoKey = "photos/sardor.jpg"
		)
	)

	private fun createInspectorAccount(employeeId: UUID): Account =
		accountRepository.save(
			Account(
				username = "insp_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.INSPECTOR,
				employeeId = employeeId,
				mustChangePassword = false,
				isActive = true
			)
		)

	@Test
	fun `INSPECTOR sees their own profile detail with real Employee fields and no fabricated rating`() {
		val employee = createEmployee()
		val account = createInspectorAccount(requireNotNull(employee.id))

		val result = profileDetailService.getMyProfileDetail(CustomUserDetails.from(account))

		assertEquals(employee.id, result.employeeId)
		assertEquals("Akbarov Sardor", result.fullName)
		assertEquals("Katta inspektor", result.position)
		assertEquals("Yo'l nazorati bo'limi", result.department)
		assertEquals("Toshkent shahri", result.regionName)
		assertEquals("+998901234567", result.phoneNumber)
		assertEquals(LocalDate.of(2023, 1, 14), result.hiredAt)
		assertEquals("photos/sardor.jpg", result.photoKey)
		// No completed inspections seeded for this inspector -> 0, not fabricated.
		assertEquals(0, result.completedInspectionsCount)
	}

	@Test
	fun `no active assigned vehicle means assignedVehicle is null`() {
		val employee = createEmployee()
		val account = createInspectorAccount(requireNotNull(employee.id))

		val result = profileDetailService.getMyProfileDetail(CustomUserDetails.from(account))

		assertNull(result.assignedVehicle)
	}

	@Test
	fun `an active vehicle assigned to this employee surfaces as assignedVehicle, an inactive one does not`() {
		val employee = createEmployee()
		val account = createInspectorAccount(requireNotNull(employee.id))

		vehicleRepository.save(
			Vehicle(
				plateNumber = "01A555BC-${UUID.randomUUID().toString().take(6)}",
				type = VehicleType.CAR,
				model = "Cobalt",
				regionName = "Toshkent",
				ownerType = VehicleOwnerType.LEGAL_ENTITY,
				assignedEmployeeId = employee.id,
				isActive = false // inactive assignment must not surface
			)
		)
		val active = vehicleRepository.save(
			Vehicle(
				plateNumber = "01A777CD-${UUID.randomUUID().toString().take(6)}",
				type = VehicleType.CAR,
				model = "Nexia",
				regionName = "Toshkent",
				ownerType = VehicleOwnerType.LEGAL_ENTITY,
				assignedEmployeeId = employee.id,
				isActive = true
			)
		)

		val result = profileDetailService.getMyProfileDetail(CustomUserDetails.from(account))

		assertEquals(active.id, result.assignedVehicle?.id)
		assertEquals("Nexia", result.assignedVehicle?.model)
	}

	@Test
	fun `recentActivity merges Incident, completed Inspection, and WorkShift events newest-first`() {
		val employee = createEmployee()
		val account = createInspectorAccount(requireNotNull(employee.id))
		val accountId = requireNotNull(account.id)

		incidentRepository.save(
			Incident(
				title = "Test incident",
				type = IncidentType.VIOLATION,
				actionType = ActionType.VIOLATION_RECORDED,
				assignedInspectorId = accountId,
				occurredAt = Instant.parse("2026-05-20T14:22:00Z")
			)
		)
		inspectionRepository.save(
			Inspection(
				checkpointId = UUID.randomUUID(),
				assignedInspectorId = accountId,
				status = InspectionStatus.COMPLETED,
				performedAt = Instant.parse("2026-05-20T12:31:00Z")
			)
		)
		// A PLANNED (not completed) inspection must NOT appear in the timeline.
		inspectionRepository.save(
			Inspection(
				checkpointId = UUID.randomUUID(),
				assignedInspectorId = accountId,
				status = InspectionStatus.PLANNED
			)
		)
		workShiftRepository.save(
			WorkShift(
				inspectorId = accountId,
				startedAt = Instant.parse("2026-05-20T08:00:00Z")
			)
		)

		val result = profileDetailService.getMyProfileDetail(CustomUserDetails.from(account))

		assertEquals(3, result.recentActivity.size, "Only the 3 real events must appear, not the PLANNED inspection")
		assertEquals("Hodisa qayd etdi", result.recentActivity[0].label)
		assertEquals("Tekshiruvni yakunladi", result.recentActivity[1].label)
		assertEquals("Navbatchilikni boshladi", result.recentActivity[2].label)
		assertTrue(
			result.recentActivity.zipWithNext().all { (a, b) -> !a.occurredAt.isBefore(b.occurredAt) },
			"Timeline must be sorted newest-first"
		)
	}

	@Test
	fun `a non-INSPECTOR caller is rejected (mirrors the controller's 403)`() {
		val admin = accountRepository.save(
			Account(
				username = "admin_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.ADMIN,
				mustChangePassword = false,
				isActive = true
			)
		)

		assertThrows(ForbiddenException::class.java) {
			profileDetailService.getMyProfileDetail(CustomUserDetails.from(admin))
		}
	}

	@Test
	fun `getMyPositionHistory returns the caller's own spells newest-first`() {
		val employee = createEmployee()
		val employeeId = requireNotNull(employee.id)
		val account = createInspectorAccount(employeeId)

		// Same writer EmployeeService.create/update use — see EmployeePositionHistoryService kdoc.
		employeePositionHistoryService.recordChange(employeeId, "Inspektor", "Toshkent shahri")
		employeePositionHistoryService.recordChange(employeeId, "Katta inspektor", "Toshkent shahri")

		val result = profileDetailService.getMyPositionHistory(CustomUserDetails.from(account))

		assertEquals(2, result.size)
		assertEquals("Katta inspektor", result[0].position)
		assertNull(result[0].endedAt)
		assertEquals("Inspektor", result[1].position)
		assertTrue(result[1].endedAt != null)
	}

	@Test
	fun `getMyPositionHistory is empty when the employee has no recorded spells yet`() {
		val employee = createEmployee()
		val account = createInspectorAccount(requireNotNull(employee.id))

		val result = profileDetailService.getMyPositionHistory(CustomUserDetails.from(account))

		assertEquals(0, result.size)
	}

	@Test
	fun `getMyPositionHistory rejects a non-INSPECTOR caller (mirrors the controller's 403)`() {
		val admin = accountRepository.save(
			Account(
				username = "admin_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.ADMIN,
				mustChangePassword = false,
				isActive = true
			)
		)

		assertThrows(ForbiddenException::class.java) {
			profileDetailService.getMyPositionHistory(CustomUserDetails.from(admin))
		}
	}
}

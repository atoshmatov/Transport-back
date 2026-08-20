package uz.safecity.transportobserver.incidents.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.entity.IncidentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the "hodisa yaratishda inspektorga tayinlash" gap reported from the web admin panel:
 * assign-on-create ([CreateIncidentRequest.assignedInspectorId]) and the [assignedInspectorName]
 * enrichment that lets the web UI show a name instead of a bare account UUID next to
 * "tayinlangan" — see IncidentService#create / #resolveInspectorNames kdoc.
 */
@SpringBootTest
@Transactional
class IncidentServiceTests {

	@Autowired
	lateinit var incidentService: IncidentService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var employeeRepository: EmployeeRepository

	private fun createInspector(fullName: String, active: Boolean = true): Account {
		val employee = employeeRepository.save(Employee(fullName = fullName))
		val account = Account(
			username = "insp_${UUID.randomUUID().toString().take(20)}",
			passwordHash = "irrelevant-for-this-test",
			role = RoleType.INSPECTOR,
			employeeId = requireNotNull(employee.id),
			mustChangePassword = false,
			isActive = active
		)
		return accountRepository.save(account)
	}

	private fun createAdmin(): Account {
		val account = Account(
			username = "admin_${UUID.randomUUID().toString().take(20)}",
			passwordHash = "irrelevant-for-this-test",
			role = RoleType.ADMIN,
			mustChangePassword = false,
			isActive = true
		)
		return accountRepository.save(account)
	}

	@Test
	fun `ADMIN can assign an inspector directly when creating an incident`() {
		val admin = createAdmin()
		val inspector = createInspector("Aliyev Vali")

		val dto = incidentService.create(
			CreateIncidentRequest(
				title = "Test incident",
				type = IncidentType.VIOLATION,
				assignedInspectorId = inspector.id
			),
			CustomUserDetails.from(admin)
		)

		assertEquals(inspector.id, dto.assignedInspectorId)
		assertEquals("Aliyev Vali", dto.assignedInspectorName)
	}

	@Test
	fun `assign-on-create rejects an inactive inspector account`() {
		val admin = createAdmin()
		val inactiveInspector = createInspector("Karimov Bek", active = false)

		assertThrows(BadRequestException::class.java) {
			incidentService.create(
				CreateIncidentRequest(
					title = "Test incident",
					type = IncidentType.VIOLATION,
					assignedInspectorId = inactiveInspector.id
				),
				CustomUserDetails.from(admin)
			)
		}
	}

	@Test
	fun `assign-on-create rejects a non-INSPECTOR account`() {
		val admin = createAdmin()
		val otherAdmin = createAdmin()

		assertThrows(BadRequestException::class.java) {
			incidentService.create(
				CreateIncidentRequest(
					title = "Test incident",
					type = IncidentType.VIOLATION,
					assignedInspectorId = otherAdmin.id
				),
				CustomUserDetails.from(admin)
			)
		}
	}

	@Test
	fun `an INSPECTOR caller's assignedInspectorId in the request is ignored, always self-assigns`() {
		val reporter = createInspector("Nazarov Doniyor")
		val someoneElse = createInspector("Yusupov Aziz")

		val dto = incidentService.create(
			CreateIncidentRequest(
				title = "Self-reported incident",
				type = IncidentType.ACCIDENT,
				assignedInspectorId = someoneElse.id
			),
			CustomUserDetails.from(reporter)
		)

		assertEquals(reporter.id, dto.assignedInspectorId)
		assertEquals("Nazarov Doniyor", dto.assignedInspectorName)
	}

	@Test
	fun `unassigned incident has no assignedInspectorName`() {
		val admin = createAdmin()

		val dto = incidentService.create(
			CreateIncidentRequest(title = "Unassigned incident", type = IncidentType.OTHER),
			CustomUserDetails.from(admin)
		)

		assertNull(dto.assignedInspectorId)
		assertNull(dto.assignedInspectorName)
	}

	@Test
	fun `assignInspector via the standalone endpoint also populates assignedInspectorName`() {
		val admin = createAdmin()
		val inspector = createInspector("Tosheva Nilufar")

		val created = incidentService.create(
			CreateIncidentRequest(title = "Unassigned first", type = IncidentType.OTHER),
			CustomUserDetails.from(admin)
		)
		assertNull(created.assignedInspectorName)

		val assigned = incidentService.assignInspector(
			created.id,
			requireNotNull(inspector.id),
			admin.id,
			RoleType.ADMIN
		)

		assertEquals(inspector.id, assigned.assignedInspectorId)
		assertEquals("Tosheva Nilufar", assigned.assignedInspectorName)
	}
}

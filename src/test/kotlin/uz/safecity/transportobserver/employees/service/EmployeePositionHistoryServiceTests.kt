package uz.safecity.transportobserver.employees.service

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.employees.dto.CreateEmployeeRequest
import uz.safecity.transportobserver.employees.dto.UpdateEmployeeRequest
import uz.safecity.transportobserver.employees.entity.EmployeePositionHistory
import uz.safecity.transportobserver.employees.repository.EmployeePositionHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Covers the lavozim/hudud o'zgarish jurnali (position/region change log) hook: [EmployeeService.create]
 * seeding the first spell, [EmployeeService.update] closing/opening spells whenever `position`/
 * `regionName` actually changes (and NOT doing so when nothing changed), and
 * [EmployeeService.getPositionHistory] (`GET /api/v1/admin/employees/{id}/position-history`).
 */
@SpringBootTest
@Transactional
class EmployeePositionHistoryServiceTests {

	@Autowired
	lateinit var employeeService: EmployeeService

	@Autowired
	lateinit var employeePositionHistoryRepository: EmployeePositionHistoryRepository

	private fun createEmployee(position: String? = "Inspektor", regionName: String? = "Toshkent shahri") =
		employeeService.create(
			CreateEmployeeRequest(
				fullName = "Test ${UUID.randomUUID().toString().take(8)}",
				position = position,
				regionName = regionName,
				role = RoleType.INSPECTOR
			),
			actorAccountId = null,
			actorRole = RoleType.SUPER_ADMIN
		).employee

	@Test
	fun `creating an employee with a position seeds a single open history spell`() {
		val employee = createEmployee(position = "Inspektor", regionName = "Toshkent shahri")

		val history = employeeService.getPositionHistory(employee.id)

		assertEquals(1, history.size)
		assertEquals("Inspektor", history[0].position)
		assertEquals("Toshkent shahri", history[0].regionName)
		assertNull(history[0].endedAt)
	}

	@Test
	fun `creating an employee with no position seeds no history row`() {
		val employee = createEmployee(position = null, regionName = null)

		assertTrue(employeeService.getPositionHistory(employee.id).isEmpty())
	}

	@Test
	fun `updating position closes the old spell and opens a new one`() {
		val employee = createEmployee(position = "Inspektor", regionName = "Toshkent shahri")

		employeeService.update(
			employee.id,
			UpdateEmployeeRequest(fullName = employee.fullName, position = "Katta inspektor", regionName = "Toshkent shahri"),
			actorAccountId = null,
			actorRole = RoleType.SUPER_ADMIN
		)

		val history = employeeService.getPositionHistory(employee.id)
		assertEquals(2, history.size, "One closed spell (Inspektor) + one open spell (Katta inspektor)")

		// getPositionHistory is newest-first.
		val current = history[0]
		val previous = history[1]
		assertEquals("Katta inspektor", current.position)
		assertNull(current.endedAt)
		assertEquals("Inspektor", previous.position)
		assertNotNull(previous.endedAt)
	}

	@Test
	fun `updating region only (same position) also opens a new spell`() {
		val employee = createEmployee(position = "Inspektor", regionName = "Toshkent shahri")

		employeeService.update(
			employee.id,
			UpdateEmployeeRequest(fullName = employee.fullName, position = "Inspektor", regionName = "Samarqand viloyati"),
			actorAccountId = null,
			actorRole = RoleType.SUPER_ADMIN
		)

		val history = employeeService.getPositionHistory(employee.id)
		assertEquals(2, history.size)
		assertEquals("Samarqand viloyati", history[0].regionName)
		assertNull(history[0].endedAt)
		assertEquals("Toshkent shahri", history[1].regionName)
		assertNotNull(history[1].endedAt)
	}

	@Test
	fun `updating with the exact same position and region leaves history untouched`() {
		val employee = createEmployee(position = "Inspektor", regionName = "Toshkent shahri")

		employeeService.update(
			employee.id,
			UpdateEmployeeRequest(fullName = "Renamed but same position", position = "Inspektor", regionName = "Toshkent shahri"),
			actorAccountId = null,
			actorRole = RoleType.SUPER_ADMIN
		)

		val history = employeeService.getPositionHistory(employee.id)
		assertEquals(1, history.size, "No position/region change -> no new spell, old one stays open")
		assertNull(history[0].endedAt)
	}

	@Test
	fun `clearing the position closes the open spell without opening a replacement`() {
		val employee = createEmployee(position = "Inspektor", regionName = "Toshkent shahri")

		employeeService.update(
			employee.id,
			UpdateEmployeeRequest(fullName = employee.fullName, position = null, regionName = "Toshkent shahri"),
			actorAccountId = null,
			actorRole = RoleType.SUPER_ADMIN
		)

		val history = employeeService.getPositionHistory(employee.id)
		assertEquals(1, history.size, "Only the closed spell — nothing meaningful to open for a null position")
		assertNotNull(history[0].endedAt)
	}

	@Test
	fun `getPositionHistory on an employee with no changes yet returns their seeded spell`() {
		val employee = createEmployee(position = "Inspektor", regionName = "Toshkent shahri")

		val history = employeeService.getPositionHistory(employee.id)

		assertEquals(1, history.size)
		assertEquals(employee.id, history[0].employeeId)
	}

	@Test
	fun `the DB itself refuses a second open history row for the same employee`() {
		val employeeId = UUID.randomUUID()
		employeePositionHistoryRepository.saveAndFlush(
			EmployeePositionHistory(employeeId = employeeId, position = "A", startedAt = Instant.now())
		)

		assertThrows(DataIntegrityViolationException::class.java) {
			employeePositionHistoryRepository.saveAndFlush(
				EmployeePositionHistory(employeeId = employeeId, position = "B", startedAt = Instant.now())
			)
		}
	}
}

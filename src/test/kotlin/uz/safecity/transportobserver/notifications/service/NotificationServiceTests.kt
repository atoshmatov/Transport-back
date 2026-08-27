package uz.safecity.transportobserver.notifications.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.service.IncidentService
import uz.safecity.transportobserver.notifications.entity.NotificationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers `PATCH /{id}/read`, `PATCH /read-all`, `GET /unread-count` (NotificationService), plus
 * the "assign an inspector -> they get notified" integration
 * (IncidentService#assignInspector -> NotificationService#notifyAssignment) that was previously
 * entirely missing (the audit's other flagged gap).
 */
@SpringBootTest
@Transactional
class NotificationServiceTests {

	@Autowired
	lateinit var notificationService: NotificationService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var employeeRepository: EmployeeRepository

	@Autowired
	lateinit var incidentService: IncidentService

	private fun createAccount(role: RoleType): Account =
		accountRepository.save(
			Account(
				username = "acc_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = role,
				mustChangePassword = false,
				isActive = true
			)
		)

	@Test
	fun `unread count starts at zero and increases with each notification`() {
		val account = createAccount(RoleType.INSPECTOR)
		assertEquals(0, notificationService.unreadCountForAccount(requireNotNull(account.id)))

		notificationService.create(requireNotNull(account.id), NotificationType.SYSTEM, "Test 1")
		notificationService.create(requireNotNull(account.id), NotificationType.SYSTEM, "Test 2")

		assertEquals(2, notificationService.unreadCountForAccount(requireNotNull(account.id)))
	}

	@Test
	fun `markAsRead flips isRead and drops the unread count`() {
		val account = createAccount(RoleType.INSPECTOR)
		val notification = notificationService.create(requireNotNull(account.id), NotificationType.SYSTEM, "Test")

		val updated = notificationService.markAsRead(requireNotNull(notification.id), requireNotNull(account.id))

		assertTrue(updated.isRead)
		assertEquals(0, notificationService.unreadCountForAccount(requireNotNull(account.id)))
	}

	@Test
	fun `markAsRead 404s on another account's notification instead of leaking it via 403`() {
		val owner = createAccount(RoleType.INSPECTOR)
		val other = createAccount(RoleType.INSPECTOR)
		val notification = notificationService.create(requireNotNull(owner.id), NotificationType.SYSTEM, "Test")

		assertThrows(ResourceNotFoundException::class.java) {
			notificationService.markAsRead(requireNotNull(notification.id), requireNotNull(other.id))
		}
	}

	@Test
	fun `markAllAsRead flips every unread notification for the caller only`() {
		val account = createAccount(RoleType.INSPECTOR)
		val other = createAccount(RoleType.INSPECTOR)
		notificationService.create(requireNotNull(account.id), NotificationType.SYSTEM, "Test 1")
		notificationService.create(requireNotNull(account.id), NotificationType.SYSTEM, "Test 2")
		notificationService.create(requireNotNull(other.id), NotificationType.SYSTEM, "Someone else's")

		val updatedCount = notificationService.markAllAsRead(requireNotNull(account.id))

		assertEquals(2, updatedCount)
		assertEquals(0, notificationService.unreadCountForAccount(requireNotNull(account.id)))
		// The other account's notification must be untouched.
		assertEquals(1, notificationService.unreadCountForAccount(requireNotNull(other.id)))
	}

	@Test
	fun `assigning an inspector to an incident notifies that inspector`() {
		val admin = createAccount(RoleType.ADMIN)
		val employee = employeeRepository.save(Employee(fullName = "Test Inspector"))
		val inspector = accountRepository.save(
			Account(
				username = "insp_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.INSPECTOR,
				employeeId = requireNotNull(employee.id),
				mustChangePassword = false,
				isActive = true
			)
		)

		val created = incidentService.create(
			CreateIncidentRequest(title = "Needs an inspector", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)

		incidentService.assignInspector(created.id, requireNotNull(inspector.id), admin.id, RoleType.ADMIN)

		val notifications = notificationService.listForAccount(requireNotNull(inspector.id))
		assertTrue(notifications.any { it.type == NotificationType.INCIDENT && it.relatedEntityId == created.id })
	}

	@Test
	fun `an INSPECTOR self-assigning on create does not get spammed with a self-notification`() {
		val employee = employeeRepository.save(Employee(fullName = "Self Reporter"))
		val inspector = accountRepository.save(
			Account(
				username = "insp_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.INSPECTOR,
				employeeId = requireNotNull(employee.id),
				mustChangePassword = false,
				isActive = true
			)
		)

		incidentService.create(
			CreateIncidentRequest(title = "Self-reported", type = IncidentType.VIOLATION),
			CustomUserDetails.from(inspector)
		)

		val notifications = notificationService.listForAccount(requireNotNull(inspector.id))
		assertTrue(notifications.none { it.type == NotificationType.INCIDENT })
	}
}

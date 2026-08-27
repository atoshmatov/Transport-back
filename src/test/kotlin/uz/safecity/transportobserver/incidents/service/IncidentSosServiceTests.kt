package uz.safecity.transportobserver.incidents.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.dto.CreateSosRequest
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.notifications.entity.NotificationType
import uz.safecity.transportobserver.notifications.repository.NotificationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Covers `POST /api/v1/inspector/me/sos` + `POST /api/v1/inspector/me/sos/{id}/cancel`
 * (IncidentService#createSos / #cancelSos) — the emergency-signal gap flagged by the backend
 * audit: INSPECTOR-only, self-scoped, the 5-second cancel window, and the ADMIN/OPERATOR
 * notification fan-out that createSos triggers via NotificationService#notifySosToAdmins.
 */
@SpringBootTest
@Transactional
class IncidentSosServiceTests {

	@Autowired
	lateinit var incidentService: IncidentService

	@Autowired
	lateinit var incidentRepository: IncidentRepository

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var notificationRepository: NotificationRepository

	private fun createInspector(): Account =
		accountRepository.save(
			Account(
				username = "insp_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.INSPECTOR,
				mustChangePassword = false,
				isActive = true
			)
		)

	private fun createAdmin(): Account =
		accountRepository.save(
			Account(
				username = "admin_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.ADMIN,
				mustChangePassword = false,
				isActive = true
			)
		)

	private fun createOperator(): Account =
		accountRepository.save(
			Account(
				username = "op_${UUID.randomUUID().toString().take(20)}",
				passwordHash = "irrelevant-for-this-test",
				role = RoleType.OPERATOR,
				mustChangePassword = false,
				isActive = true
			)
		)

	@Test
	fun `createSos creates a SECURITY DANGER_REPORTED incident self-assigned to the caller`() {
		val inspector = createInspector()

		val dto = incidentService.createSos(
			CreateSosRequest(latitude = 41.3, longitude = 69.2),
			CustomUserDetails.from(inspector)
		)

		assertEquals(IncidentType.SECURITY, dto.type)
		assertEquals(IncidentStatus.NEW, dto.status)
		assertTrue(dto.isSos)
		assertEquals(inspector.id, dto.assignedInspectorId)
		assertEquals(41.3, dto.latitude)
		assertEquals(69.2, dto.longitude)
	}

	@Test
	fun `createSos works with no location at all (GPS off)`() {
		val inspector = createInspector()

		val dto = incidentService.createSos(CreateSosRequest(), CustomUserDetails.from(inspector))

		assertTrue(dto.isSos)
		assertEquals(null, dto.latitude)
		assertEquals(null, dto.longitude)
	}

	@Test
	fun `createSos notifies every active ADMIN and OPERATOR`() {
		val inspector = createInspector()
		val admin = createAdmin()
		val operator = createOperator()

		val dto = incidentService.createSos(CreateSosRequest(), CustomUserDetails.from(inspector))

		val adminNotifications = notificationRepository.findByRecipientAccountIdOrderByCreatedAtDesc(requireNotNull(admin.id))
		val operatorNotifications = notificationRepository.findByRecipientAccountIdOrderByCreatedAtDesc(requireNotNull(operator.id))

		assertTrue(adminNotifications.any { it.type == NotificationType.SOS && it.relatedEntityId == dto.id })
		assertTrue(operatorNotifications.any { it.type == NotificationType.SOS && it.relatedEntityId == dto.id })
	}

	@Test
	fun `createSos is forbidden for a non-INSPECTOR caller`() {
		val admin = createAdmin()

		assertThrows(ForbiddenException::class.java) {
			incidentService.createSos(CreateSosRequest(), CustomUserDetails.from(admin))
		}
	}

	@Test
	fun `cancelSos within the window flips status to REJECTED`() {
		val inspector = createInspector()
		val created = incidentService.createSos(CreateSosRequest(), CustomUserDetails.from(inspector))

		val cancelled = incidentService.cancelSos(created.id, CustomUserDetails.from(inspector))

		assertEquals(IncidentStatus.REJECTED, cancelled.status)
	}

	@Test
	fun `cancelSos rejects after the 5-second window has elapsed`() {
		val inspector = createInspector()
		val created = incidentService.createSos(CreateSosRequest(), CustomUserDetails.from(inspector))

		// Simulate the window having elapsed by backdating the managed entity's createdAt within
		// this same transaction (createdAt is `updatable = false`, so this never hits the DB column
		// itself — it only needs to be visible to the same-session lookup cancelSos performs).
		val incident = incidentRepository.findById(created.id).orElseThrow()
		incident.createdAt = Instant.now().minusSeconds(10)

		assertThrows(ConflictException::class.java) {
			incidentService.cancelSos(created.id, CustomUserDetails.from(inspector))
		}
	}

	@Test
	fun `cancelSos rejects a non-SOS incident`() {
		val inspector = createInspector()
		val ordinary = incidentService.create(
			CreateIncidentRequest(title = "Ordinary incident", type = IncidentType.VIOLATION),
			CustomUserDetails.from(inspector)
		)

		assertThrows(BadRequestException::class.java) {
			incidentService.cancelSos(ordinary.id, CustomUserDetails.from(inspector))
		}
	}

	@Test
	fun `cancelSos rejects an already-processed SOS`() {
		val inspector = createInspector()
		val created = incidentService.createSos(CreateSosRequest(), CustomUserDetails.from(inspector))
		incidentService.cancelSos(created.id, CustomUserDetails.from(inspector))

		assertThrows(ConflictException::class.java) {
			incidentService.cancelSos(created.id, CustomUserDetails.from(inspector))
		}
	}

	@Test
	fun `cancelSos 404s on another inspector's SOS instead of leaking it via 403`() {
		val reporter = createInspector()
		val other = createInspector()
		val created = incidentService.createSos(CreateSosRequest(), CustomUserDetails.from(reporter))

		assertThrows(ResourceNotFoundException::class.java) {
			incidentService.cancelSos(created.id, CustomUserDetails.from(other))
		}
	}
}

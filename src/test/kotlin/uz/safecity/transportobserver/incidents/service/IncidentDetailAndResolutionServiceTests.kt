package uz.safecity.transportobserver.incidents.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.dto.UpdateIncidentResolutionRequest
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.vehicles.entity.Vehicle
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
import java.time.LocalDate
import java.util.UUID

/**
 * Covers the mobile "Hodisa kartasi" (incidentDetail) backend gap: [Incident.vehicleId] /
 * [Incident.passengerCount], the "ko'rilgan chora" resolution snapshot
 * (`PATCH /incidents/{id}/resolution`, ADMIN/OPERATOR/SUPER_ADMIN only), and the
 * [uz.safecity.transportobserver.incidents.entity.IncidentStatusEvent] status-history timeline
 * written at every state-changing IncidentService call — see IncidentService kdocs for each.
 */
@SpringBootTest
@Transactional
class IncidentDetailAndResolutionServiceTests {

	@Autowired
	lateinit var incidentService: IncidentService

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var vehicleRepository: VehicleRepository

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

	private fun createVehicle(plateNumber: String = "01A${(1000..9999).random()}BC"): Vehicle =
		vehicleRepository.save(
			Vehicle(
				plateNumber = plateNumber,
				type = VehicleType.BUS,
				model = "ISUZU NQR"
			)
		)

	@Test
	fun `create persists vehicleId and passengerCount, getById resolves plate and model`() {
		val inspector = createInspector()
		val vehicle = createVehicle()

		val created = incidentService.create(
			CreateIncidentRequest(
				title = "Haddan tashqari yo'lovchi",
				type = IncidentType.VIOLATION,
				vehicleId = vehicle.id,
				passengerCount = 32
			),
			CustomUserDetails.from(inspector)
		)

		val detail = incidentService.getById(created.id, CustomUserDetails.from(inspector))
		assertEquals(vehicle.id, detail.vehicleId)
		assertEquals(32, detail.passengerCount)
		assertEquals(vehicle.plateNumber, detail.vehiclePlateNumber)
		assertEquals("ISUZU NQR", detail.vehicleModel)
	}

	@Test
	fun `create rejects a non-existent vehicleId`() {
		val inspector = createInspector()

		assertThrows(BadRequestException::class.java) {
			incidentService.create(
				CreateIncidentRequest(
					title = "Bad vehicle ref",
					type = IncidentType.VIOLATION,
					vehicleId = UUID.randomUUID()
				),
				CustomUserDetails.from(inspector)
			)
		}
	}

	@Test
	fun `getById on an incident with no vehicle has null vehicle fields`() {
		val admin = createAdmin()

		val created = incidentService.create(
			CreateIncidentRequest(title = "No vehicle", type = IncidentType.OTHER),
			CustomUserDetails.from(admin)
		)

		val detail = incidentService.getById(created.id, CustomUserDetails.from(admin))
		assertNull(detail.vehicleId)
		assertNull(detail.vehiclePlateNumber)
		assertNull(detail.vehicleModel)
		assertNull(detail.passengerCount)
	}

	@Test
	fun `status history records Qayd etildi right after create`() {
		val inspector = createInspector()

		val created = incidentService.create(
			CreateIncidentRequest(title = "New report", type = IncidentType.VIOLATION),
			CustomUserDetails.from(inspector)
		)

		val detail = incidentService.getById(created.id, CustomUserDetails.from(inspector))
		assertEquals(1, detail.statusHistory.size)
		assertEquals("Qayd etildi", detail.statusHistory.first().label)
		assertEquals(IncidentStatus.NEW, detail.statusHistory.first().status)
		assertEquals(inspector.id, detail.statusHistory.first().actorAccountId)
	}

	@Test
	fun `createSos writes both Qayd etildi and Markaziy tizimga yuborildi events`() {
		val inspector = createInspector()

		val created = incidentService.createSos(
			uz.safecity.transportobserver.incidents.dto.CreateSosRequest(type = IncidentType.SECURITY),
			CustomUserDetails.from(inspector)
		)

		val detail = incidentService.getById(created.id, CustomUserDetails.from(inspector))
		val labels = detail.statusHistory.map { it.label }
		assertEquals(listOf("Qayd etildi", "Markaziy tizimga yuborildi"), labels)
		// The automatic broadcast entry is system-triggered, not attributed to the inspector.
		assertNull(detail.statusHistory.last().actorAccountId)
	}

	@Test
	fun `assignInspector appends a Boshqarma tomonidan ko'rildi event`() {
		val admin = createAdmin()
		val inspector = createInspector()

		val created = incidentService.create(
			CreateIncidentRequest(title = "Unassigned", type = IncidentType.OTHER),
			CustomUserDetails.from(admin)
		)
		incidentService.assignInspector(created.id, requireNotNull(inspector.id), admin.id, RoleType.ADMIN)

		val detail = incidentService.getById(created.id, CustomUserDetails.from(admin))
		val labels = detail.statusHistory.map { it.label }
		assertEquals(listOf("Qayd etildi", "Boshqarma tomonidan ko'rildi"), labels)
	}

	@Test
	fun `updateStatus appends the matching timeline label`() {
		val admin = createAdmin()

		val created = incidentService.create(
			CreateIncidentRequest(title = "Progressing", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)
		incidentService.updateStatus(created.id, IncidentStatus.IN_PROGRESS, admin.id, RoleType.ADMIN)

		val detail = incidentService.getById(created.id, CustomUserDetails.from(admin))
		val last = detail.statusHistory.last()
		assertEquals("Ko'rib chiqilmoqda", last.label)
		assertEquals(IncidentStatus.IN_PROGRESS, last.status)
	}

	@Test
	fun `updateResolution persists the resolution snapshot and appends Chora ko'rilmoqda`() {
		val admin = createAdmin()
		val responsible = createOperator()

		val created = incidentService.create(
			CreateIncidentRequest(title = "Needs resolution", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)

		val deadline = LocalDate.now().plusDays(7)
		incidentService.updateResolution(
			created.id,
			UpdateIncidentResolutionRequest(
				resolutionNote = "Bayonnoma tuzildi",
				fineAmount = "1 BHM",
				resolutionDeadline = deadline,
				resolutionResponsibleAccountId = responsible.id
			),
			CustomUserDetails.from(admin)
		)

		val detail = incidentService.getById(created.id, CustomUserDetails.from(admin))
		assertEquals("Bayonnoma tuzildi", detail.resolutionNote)
		assertEquals("1 BHM", detail.fineAmount)
		assertEquals(deadline, detail.resolutionDeadline)
		assertEquals(responsible.id, detail.resolutionResponsibleAccountId)
		assertTrue(detail.statusHistory.any { it.label == "Chora ko'rilmoqda" })
	}

	@Test
	fun `updateResolution is forbidden for an INSPECTOR caller`() {
		val admin = createAdmin()
		val inspector = createInspector()

		val created = incidentService.create(
			CreateIncidentRequest(title = "Not yours to resolve", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)

		assertThrows(ForbiddenException::class.java) {
			incidentService.updateResolution(
				created.id,
				UpdateIncidentResolutionRequest(resolutionNote = "Should not work"),
				CustomUserDetails.from(inspector)
			)
		}
	}

	@Test
	fun `updateResolution works for OPERATOR`() {
		val operator = createOperator()

		val created = incidentService.create(
			CreateIncidentRequest(title = "Operator resolves", type = IncidentType.VIOLATION),
			CustomUserDetails.from(operator)
		)

		val updated = incidentService.updateResolution(
			created.id,
			UpdateIncidentResolutionRequest(resolutionNote = "Ogohlantirish berildi"),
			CustomUserDetails.from(operator)
		)

		assertEquals(created.id, updated.id)
	}

	@Test
	fun `updateResolution rejects a non-existent resolutionResponsibleAccountId`() {
		val admin = createAdmin()

		val created = incidentService.create(
			CreateIncidentRequest(title = "Bad responsible ref", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)

		assertThrows(BadRequestException::class.java) {
			incidentService.updateResolution(
				created.id,
				UpdateIncidentResolutionRequest(resolutionResponsibleAccountId = UUID.randomUUID()),
				CustomUserDetails.from(admin)
			)
		}
	}
}

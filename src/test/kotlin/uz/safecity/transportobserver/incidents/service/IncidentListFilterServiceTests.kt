package uz.safecity.transportobserver.incidents.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.dto.CreateSosRequest
import uz.safecity.transportobserver.incidents.entity.IncidentStatus
import uz.safecity.transportobserver.incidents.entity.IncidentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers the `GET /incidents?isSos=true` filter added for the web "Favqulodda navbat"
 * (Emergency) screen — see IncidentService#list / #buildSpecification kdoc. Before this, the
 * frontend had no server-side way to select only SOS reports and had to filter the last 100
 * incidents client-side, which could miss older still-open SOS reports once volume grew.
 */
@SpringBootTest
@Transactional
class IncidentListFilterServiceTests {

	@Autowired
	lateinit var incidentService: IncidentService

	@Autowired
	lateinit var accountRepository: AccountRepository

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

	@Test
	fun `isSos=true returns only SOS incidents, excluding ordinary reports`() {
		val admin = createAdmin()
		val inspector = createInspector()

		incidentService.create(
			CreateIncidentRequest(title = "Ordinary report", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)
		val sos = incidentService.createSos(
			CreateSosRequest(type = IncidentType.SECURITY),
			CustomUserDetails.from(inspector)
		)

		val page = incidentService.list(
			status = null,
			type = null,
			assignedInspectorId = null,
			isSos = true,
			principal = CustomUserDetails.from(admin),
			pageable = PageRequest.of(0, 20)
		)

		assertTrue(page.content.isNotEmpty())
		assertTrue(page.content.all { it.isSos })
		assertTrue(page.content.any { it.id == sos.id })
	}

	@Test
	fun `isSos omitted (null) preserves the previous unfiltered behavior`() {
		val admin = createAdmin()
		val inspector = createInspector()

		incidentService.create(
			CreateIncidentRequest(title = "Ordinary report", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)
		incidentService.createSos(
			CreateSosRequest(type = IncidentType.SECURITY),
			CustomUserDetails.from(inspector)
		)

		val page = incidentService.list(
			status = null,
			type = null,
			assignedInspectorId = null,
			isSos = null,
			principal = CustomUserDetails.from(admin),
			pageable = PageRequest.of(0, 20)
		)

		assertEquals(2, page.totalElements)
	}

	@Test
	fun `isSos=false returns only non-SOS incidents`() {
		val admin = createAdmin()
		val inspector = createInspector()

		val ordinary = incidentService.create(
			CreateIncidentRequest(title = "Ordinary report", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)
		incidentService.createSos(
			CreateSosRequest(type = IncidentType.SECURITY),
			CustomUserDetails.from(inspector)
		)

		val page = incidentService.list(
			status = null,
			type = null,
			assignedInspectorId = null,
			isSos = false,
			principal = CustomUserDetails.from(admin),
			pageable = PageRequest.of(0, 20)
		)

		assertTrue(page.content.isNotEmpty())
		assertTrue(page.content.none { it.isSos })
		assertTrue(page.content.any { it.id == ordinary.id })
	}

	@Test
	fun `isSos=true combined with status=NEW returns only incidents matching both predicates`() {
		val admin = createAdmin()
		val inspector = createInspector()

		// Ordinary, non-SOS, status=NEW — must be excluded (isSos predicate fails).
		incidentService.create(
			CreateIncidentRequest(title = "Ordinary report", type = IncidentType.VIOLATION),
			CustomUserDetails.from(admin)
		)
		// SOS, status=NEW (the only row matching BOTH filters).
		val sosNew = incidentService.createSos(
			CreateSosRequest(type = IncidentType.SECURITY),
			CustomUserDetails.from(inspector)
		)
		// SOS but moved to IN_PROGRESS — must be excluded (status predicate fails), proving the two
		// predicates are combined with AND rather than either one alone deciding the result.
		val sosInProgress = incidentService.createSos(
			CreateSosRequest(type = IncidentType.SECURITY),
			CustomUserDetails.from(inspector)
		)
		incidentService.updateStatus(
			requireNotNull(sosInProgress.id),
			IncidentStatus.IN_PROGRESS,
			admin.id,
			RoleType.ADMIN
		)

		val page = incidentService.list(
			status = IncidentStatus.NEW,
			type = null,
			assignedInspectorId = null,
			isSos = true,
			principal = CustomUserDetails.from(admin),
			pageable = PageRequest.of(0, 20)
		)

		assertTrue(page.content.isNotEmpty())
		assertTrue(page.content.all { it.isSos && it.status == IncidentStatus.NEW })
		assertTrue(page.content.any { it.id == sosNew.id })
		assertTrue(page.content.none { it.id == sosInProgress.id })
	}

	@Test
	fun `isSos=true as INSPECTOR only returns SOS incidents assigned to that inspector, not another inspector's SOS`() {
		val inspectorA = createInspector()
		val inspectorB = createInspector()

		val sosA = incidentService.createSos(
			CreateSosRequest(type = IncidentType.SECURITY),
			CustomUserDetails.from(inspectorA)
		)
		val sosB = incidentService.createSos(
			CreateSosRequest(type = IncidentType.SECURITY),
			CustomUserDetails.from(inspectorB)
		)

		val page = incidentService.list(
			status = null,
			type = null,
			assignedInspectorId = null,
			isSos = true,
			principal = CustomUserDetails.from(inspectorA),
			pageable = PageRequest.of(0, 20)
		)

		assertTrue(page.content.isNotEmpty())
		assertTrue(page.content.all { it.isSos && it.assignedInspectorId == inspectorA.id })
		assertTrue(page.content.any { it.id == sosA.id })
		assertTrue(page.content.none { it.id == sosB.id })
	}
}

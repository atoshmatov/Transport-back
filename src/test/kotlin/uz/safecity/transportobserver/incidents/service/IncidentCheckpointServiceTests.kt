package uz.safecity.transportobserver.incidents.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.common.exception.BadRequestException
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
 * Covers [uz.safecity.transportobserver.incidents.entity.Incident.checkpointId] — the map's
 * per-checkpoint "xavf darajasi" (risk level) rollup gap this closes. Product's HYBRID rule (see
 * that field's kdoc): GPS-proximity is a client-side, user-confirmable SUGGESTION only
 * (`GET /api/v1/checkpoints/nearby`) — the backend itself never computes or infers a checkpoint,
 * it only validates and persists whatever [CreateIncidentRequest.checkpointId] the caller
 * explicitly sent.
 */
@SpringBootTest
@Transactional
class IncidentCheckpointServiceTests {

	@Autowired
	lateinit var incidentService: IncidentService

	@Autowired
	lateinit var checkpointService: CheckpointService

	@Autowired
	lateinit var accountRepository: AccountRepository

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

	private fun createCheckpoint(name: String = "Test nazorat punkti ${UUID.randomUUID()}") =
		checkpointService.create(CreateCheckpointRequest(name = name, latitude = 41.3, longitude = 69.2))

	@Test
	fun `create persists an explicitly confirmed checkpointId`() {
		val inspector = createInspector()
		val checkpoint = createCheckpoint()

		val created = incidentService.create(
			CreateIncidentRequest(
				title = "Reported at checkpoint",
				type = IncidentType.VIOLATION,
				checkpointId = checkpoint.id
			),
			CustomUserDetails.from(inspector)
		)

		assertEquals(checkpoint.id, created.checkpointId)

		val detail = incidentService.getById(created.id, CustomUserDetails.from(inspector))
		assertEquals(checkpoint.id, detail.checkpointId)
	}

	@Test
	fun `create rejects a non-existent checkpointId`() {
		val inspector = createInspector()

		assertThrows(BadRequestException::class.java) {
			incidentService.create(
				CreateIncidentRequest(
					title = "Bad checkpoint ref",
					type = IncidentType.VIOLATION,
					checkpointId = UUID.randomUUID()
				),
				CustomUserDetails.from(inspector)
			)
		}
	}

	@Test
	fun `create leaves checkpointId null when the caller omits it (never guessed by proximity)`() {
		val inspector = createInspector()

		val created = incidentService.create(
			CreateIncidentRequest(title = "No checkpoint given", type = IncidentType.OTHER),
			CustomUserDetails.from(inspector)
		)

		assertNull(created.checkpointId)
	}
}

package uz.safecity.transportobserver.checkpoints.service

import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers `GET /api/v1/checkpoints/nearby` (`CheckpointService.findNearby` /
 * `CheckpointRepository.findNearestActive`) — the mobile "hodisa yaratish" flow's GPS-proximity
 * checkpoint SUGGESTION list. See [uz.safecity.transportobserver.incidents.entity.Incident.checkpointId]
 * kdoc for why this must stay a pure, read-only ranking: nothing here ever writes an incident's
 * checkpoint on the caller's behalf — that only happens via [CreateIncidentRequest.checkpointId]
 * (see [uz.safecity.transportobserver.incidents.service.IncidentCheckpointServiceTests] instead
 * for that side).
 */
@SpringBootTest
@Transactional
class CheckpointNearbyServiceTests {

	@Autowired
	lateinit var checkpointService: CheckpointService

	private fun createCheckpoint(name: String, latitude: Double, longitude: Double) =
		checkpointService.create(CreateCheckpointRequest(name = name, latitude = latitude, longitude = longitude))

	@Test
	fun `findNearby orders active checkpoints by real distance ascending`() {
		// Reference point: Tashkent city center-ish.
		val referenceLat = 41.311
		val referenceLng = 69.279

		val near = createCheckpoint("Near", referenceLat + 0.001, referenceLng + 0.001)
		val medium = createCheckpoint("Medium", referenceLat + 0.05, referenceLng + 0.05)
		val far = createCheckpoint("Far", referenceLat + 1.0, referenceLng + 1.0)

		// Max clamp (see CheckpointService.findNearby) — isolate just our 3 test checkpoints from
		// the ranking by id/distance rather than assuming an empty dev DB, so this stays reliable
		// against whatever else happens to already exist in the local Postgres this test runs against.
		val result = checkpointService.findNearby(referenceLat, referenceLng, limit = 50)
		val ours = result.filter { it.checkpointId in setOf(near.id, medium.id, far.id) }

		assertEquals(3, ours.size, "all 3 test checkpoints must appear in the top 50 nearest to the reference point")
		assertEquals(listOf(near.id, medium.id, far.id), ours.map { it.checkpointId })
		// Distance must actually increase along the ranking, not just be a stable/arbitrary order.
		assertTrue(ours.zipWithNext().all { (a, b) -> a.distanceMeters <= b.distanceMeters })
	}

	@Test
	fun `findNearby excludes a deactivated checkpoint`() {
		val referenceLat = 41.311
		val referenceLng = 69.279
		val checkpoint = createCheckpoint("To be deactivated", referenceLat, referenceLng)
		checkpointService.updateStatus(checkpoint.id, false)

		val result = checkpointService.findNearby(referenceLat, referenceLng, limit = 10)

		assertTrue(result.none { it.checkpointId == checkpoint.id })
	}

	@Test
	fun `findNearby respects the limit parameter`() {
		val referenceLat = 41.311
		val referenceLng = 69.279
		repeat(5) { i -> createCheckpoint("Limit test $i ${UUID.randomUUID()}", referenceLat + i * 0.01, referenceLng) }

		val result = checkpointService.findNearby(referenceLat, referenceLng, limit = 2)

		assertEquals(2, result.size)
	}

	@Test
	fun `findNearby rejects an out-of-range latitude`() {
		assertThrows(BadRequestException::class.java) {
			checkpointService.findNearby(latitude = 200.0, longitude = 69.279, limit = 5)
		}
	}
}

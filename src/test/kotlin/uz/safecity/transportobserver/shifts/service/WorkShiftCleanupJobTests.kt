package uz.safecity.transportobserver.shifts.service

import uz.safecity.transportobserver.shifts.entity.WorkShift
import uz.safecity.transportobserver.shifts.repository.WorkShiftRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Covers the O'RTA gap flagged by the security audit: a [WorkShift] that never gets an explicit
 * `end()` call (app killed, device died, lost connectivity for good) used to stay open forever,
 * leaving that inspector's `onDuty` signal permanently `true`. [WorkShiftCleanupJob] now closes
 * any shift open longer than [WorkShiftCleanupJob.STALE_AFTER].
 *
 * Rows are inserted directly via [workShiftRepository] (not [WorkShiftService], which always
 * stamps `startedAt = Instant.now()`) so a shift can be backdated past the staleness threshold
 * without waiting real time — same "no persisted Account needed, inspector_id has no FK" reasoning
 * as [WorkShiftServiceTests], and cleaned up explicitly in [cleanup] rather than relying on
 * `@Transactional` rollback, since the job itself runs (and commits) in its own transaction.
 */
@SpringBootTest
class WorkShiftCleanupJobTests {

	@Autowired
	lateinit var workShiftCleanupJob: WorkShiftCleanupJob

	@Autowired
	lateinit var workShiftRepository: WorkShiftRepository

	private val createdShiftIds = mutableListOf<UUID>()

	@AfterEach
	fun cleanup() {
		if (createdShiftIds.isNotEmpty()) {
			workShiftRepository.deleteAllById(createdShiftIds)
			createdShiftIds.clear()
		}
	}

	private fun persistShift(startedAt: Instant, endedAt: Instant? = null): WorkShift {
		val shift = workShiftRepository.save(
			WorkShift(inspectorId = UUID.randomUUID(), startedAt = startedAt, endedAt = endedAt)
		)
		createdShiftIds += requireNotNull(shift.id)
		return shift
	}

	@Test
	fun `closes a shift open longer than the staleness threshold`() {
		// Truncated to microseconds: Postgres' timestamp column stores microsecond precision, but
		// Instant.now() can carry sub-microsecond precision on some JVMs — comparing an untruncated
		// in-memory Instant against the DB-round-tripped value would flake on that nanosecond noise.
		val startedAt = Instant.now().minus(WorkShiftCleanupJob.STALE_AFTER).minusSeconds(3600)
			.truncatedTo(ChronoUnit.MICROS)
		val stale = persistShift(startedAt)

		workShiftCleanupJob.closeStaleShifts()

		val reloaded = workShiftRepository.findById(requireNotNull(stale.id)).orElseThrow()
		assertNotNull(reloaded.endedAt, "A shift older than the staleness threshold must be auto-closed")
		// Fixed cutoff (startedAt + STALE_AFTER), not "now" — see WorkShiftCleanupJob kdoc.
		assertEquals(startedAt.plus(WorkShiftCleanupJob.STALE_AFTER), reloaded.endedAt)
	}

	@Test
	fun `leaves a recently-started open shift untouched`() {
		val fresh = persistShift(startedAt = Instant.now().minusSeconds(60))

		workShiftCleanupJob.closeStaleShifts()

		val reloaded = workShiftRepository.findById(requireNotNull(fresh.id)).orElseThrow()
		assertNull(reloaded.endedAt, "A shift well within the staleness threshold must stay open")
	}

	@Test
	fun `leaves an already-closed shift untouched`() {
		val startedAt = Instant.now().minus(WorkShiftCleanupJob.STALE_AFTER).minusSeconds(3600)
			.truncatedTo(ChronoUnit.MICROS)
		val originalEndedAt = startedAt.plusSeconds(1800)
		val alreadyClosed = persistShift(startedAt = startedAt, endedAt = originalEndedAt)

		workShiftCleanupJob.closeStaleShifts()

		val reloaded = workShiftRepository.findById(requireNotNull(alreadyClosed.id)).orElseThrow()
		assertEquals(originalEndedAt, reloaded.endedAt, "An already-closed shift must never be touched by the cleanup job")
	}
}

package uz.safecity.transportobserver.shifts.service

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.shifts.dto.WorkShiftDto
import uz.safecity.transportobserver.shifts.repository.WorkShiftRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.transaction.annotation.Transactional
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Covers the HIGH gap flagged by QA on [WorkShiftService.startShift]: the previous
 * find-then-save (`findByInspectorIdAndEndedAtIsNull` + `save`) raced under concurrent calls for
 * the same inspector — two parallel `POST /inspector/me/shift/start` calls (e.g. a mobile client
 * retrying after a network timeout) could both observe "no open shift" and both insert, breaking
 * the "at most one open shift per inspector" rule (see
 * [uz.safecity.transportobserver.shifts.entity.WorkShift] kdoc). Now backed by
 * [WorkShiftRepository.startIfNoOpenShift] (atomic `INSERT ... ON CONFLICT` against the
 * `ux_work_shifts_inspector_open` partial unique index bootstrapped by
 * [uz.safecity.transportobserver.shifts.config.WorkShiftSchemaInitializer]).
 *
 * No persisted [uz.safecity.transportobserver.auth.entity.Account] is needed: `inspector_id` on
 * `work_shifts` carries no FK constraint (same convention as
 * [uz.safecity.transportobserver.map.entity.InspectorLocation.inspectorId]), and role-gating
 * reads straight off the in-memory [CustomUserDetails.role], not the DB — so an arbitrary UUID +
 * a directly-constructed principal is enough to exercise the race.
 *
 * The concurrent test deliberately does NOT run inside the surrounding `@Transactional` test
 * rollback: each worker thread's [WorkShiftService.startShift] call opens its own real,
 * independently-committed transaction (Spring's transaction sync is per-thread), so wrapping the
 * test method itself in a transaction would neither include nor roll those back — it would just
 * be pointless ceremony. Rows are cleaned up explicitly in [cleanup] instead.
 */
@SpringBootTest
class WorkShiftServiceTests {

	@Autowired
	lateinit var workShiftService: WorkShiftService

	@Autowired
	lateinit var workShiftRepository: WorkShiftRepository

	@Autowired
	lateinit var checkpointService: CheckpointService

	private val createdShiftIds = mutableListOf<UUID>()

	@AfterEach
	fun cleanup() {
		if (createdShiftIds.isNotEmpty()) {
			workShiftRepository.deleteAllById(createdShiftIds)
			createdShiftIds.clear()
		}
	}

	private fun inspectorPrincipal(accountId: UUID = UUID.randomUUID()): CustomUserDetails = CustomUserDetails(
		accountId = accountId,
		role = RoleType.INSPECTOR,
		username = "insp_${accountId.toString().take(8)}",
		passwordHash = "irrelevant-for-this-test",
		authorities = listOf(SimpleGrantedAuthority(RoleType.INSPECTOR.authority)),
		mustChangePassword = false,
		enabled = true,
		accountNonLocked = true
	)

	@Test
	@Transactional
	fun `startShift throws ConflictException on a second sequential call for the same inspector`() {
		val principal = inspectorPrincipal()

		val first = workShiftService.startShift(principal)
		createdShiftIds += first.id
		assertNotNull(first.id)
		assertEquals(principal.accountId, first.inspectorId)
		assertNull(first.endedAt)

		val ex = assertThrows(ConflictException::class.java) {
			workShiftService.startShift(principal)
		}
		assertEquals("error.shift.already-open", ex.messageKey)

		// Exactly one open shift for this inspector must remain in the DB — the failed second
		// call must not have inserted anything.
		val open = workShiftRepository.findByInspectorIdAndEndedAtIsNull(principal.accountId)
		assertNotNull(open)
		assertEquals(first.id, open?.id)
	}

	@Test
	fun `startShift under real concurrent calls lets exactly one succeed`() {
		val principal = inspectorPrincipal()
		val threadCount = 8
		val executor = Executors.newFixedThreadPool(threadCount)
		val readyLatch = CountDownLatch(threadCount)
		val startLatch = CountDownLatch(1)
		val results = Collections.synchronizedList(mutableListOf<Result<WorkShiftDto>>())

		try {
			val futures = (1..threadCount).map {
				executor.submit {
					readyLatch.countDown()
					startLatch.await()
					results += runCatching { workShiftService.startShift(principal) }
				}
			}
			readyLatch.await(5, TimeUnit.SECONDS)
			startLatch.countDown() // release every worker thread at (as close to) the same instant
			futures.forEach { it.get(10, TimeUnit.SECONDS) }
		} finally {
			executor.shutdown()
		}

		val successes = results.filter { it.isSuccess }
		val conflicts = results.filter { it.exceptionOrNull() is ConflictException }

		assertEquals(1, successes.size, "Exactly one of the $threadCount concurrent startShift calls must succeed")
		assertEquals(
			threadCount - 1,
			conflicts.size,
			"Every other concurrent call must fail with ConflictException, not silently insert a second open shift"
		)

		successes.first().getOrNull()?.let { createdShiftIds += it.id }

		// DB-level proof, independent of what the app observed: still exactly one open shift row.
		val open = workShiftRepository.findByInspectorIdAndEndedAtIsNull(principal.accountId)
		assertNotNull(open)
		assertEquals(successes.first().getOrNull()?.id, open?.id)
	}

	@Test
	@Transactional
	fun `startShift with a valid checkpointId persists the checkpoint check-in`() {
		val checkpoint = checkpointService.create(
			CreateCheckpointRequest(name = "Test nazorat punkti", latitude = 41.3, longitude = 69.2)
		)
		val principal = inspectorPrincipal()

		val dto = workShiftService.startShift(principal, checkpoint.id)
		createdShiftIds += dto.id

		assertEquals(checkpoint.id, dto.checkpointId)
		val persisted = workShiftRepository.findByInspectorIdAndEndedAtIsNull(principal.accountId)
		assertNotNull(persisted)
		assertEquals(checkpoint.id, persisted?.checkpointId)
	}

	@Test
	@Transactional
	fun `startShift with no checkpointId leaves the check-in null`() {
		val principal = inspectorPrincipal()

		val dto = workShiftService.startShift(principal)
		createdShiftIds += dto.id

		assertNull(dto.checkpointId)
		val persisted = workShiftRepository.findByInspectorIdAndEndedAtIsNull(principal.accountId)
		assertNull(persisted?.checkpointId)
	}

	@Test
	@Transactional
	fun `startShift with an unknown checkpointId throws BadRequestException and inserts nothing`() {
		val principal = inspectorPrincipal()
		val unknownCheckpointId = UUID.randomUUID()

		assertThrows(BadRequestException::class.java) {
			workShiftService.startShift(principal, unknownCheckpointId)
		}

		assertNull(workShiftRepository.findByInspectorIdAndEndedAtIsNull(principal.accountId))
	}
}

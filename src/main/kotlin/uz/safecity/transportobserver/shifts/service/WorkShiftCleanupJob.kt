package uz.safecity.transportobserver.shifts.service

import uz.safecity.transportobserver.shifts.repository.WorkShiftRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Auto-closes work shifts an inspector never explicitly ended via `POST
 * /api/v1/inspector/me/shift/end` — e.g. the mobile app was force-killed, lost connectivity for
 * good, or the device died mid-shift. Without this, [uz.safecity.transportobserver.shifts.entity.WorkShift.endedAt]
 * stays `null` forever, so that inspector's `onDuty` (see [WorkShift] kdoc) would read `true` on
 * every admin/map view indefinitely — a stale "on duty" signal nobody can act on, since the
 * inspector already stopped using the app.
 *
 * Runs hourly (`@EnableScheduling` is already on in [uz.safecity.transportobserver.TransportObserverApplication]),
 * closing every open shift whose [uz.safecity.transportobserver.shifts.entity.WorkShift.startedAt]
 * is more than [STALE_AFTER] ago — a normal shift is expected to last a work day at most, so
 * anything still open after 16h is treated as abandoned rather than a legitimately long shift.
 *
 * [WorkShift.endedAt][uz.safecity.transportobserver.shifts.entity.WorkShift.endedAt] is set to
 * `startedAt + STALE_AFTER` rather than "now" — this job only runs once an hour, so "now" could be
 * up to an hour later than the moment the shift actually became stale, and would make the
 * recorded shift duration drift with however late the job happened to run instead of reflecting a
 * fixed, predictable cutoff.
 */
@Component
class WorkShiftCleanupJob(
	private val workShiftRepository: WorkShiftRepository
) {

	private val log = LoggerFactory.getLogger(WorkShiftCleanupJob::class.java)

	@Scheduled(fixedRate = ONE_HOUR_MILLIS)
	@Transactional
	fun closeStaleShifts() {
		// Explicit try/catch (rather than relying on Spring's default scheduled-task exception
		// handler, which just swallows the exception) so a failure is always visible in the logs
		// at ERROR level with a full stack trace — there's no monitoring/alerting on this job yet,
		// so a silent swallow would mean stale shifts pile up with nobody noticing. Caught here
		// (inside the @Transactional method) rather than in an outer wrapper method, since an
		// outer wrapper would call this method via a plain self-invocation that bypasses Spring's
		// transactional proxy entirely.
		try {
			val cutoff = Instant.now().minus(STALE_AFTER)
			val staleShifts = workShiftRepository.findByEndedAtIsNullAndStartedAtBefore(cutoff)
			if (staleShifts.isEmpty()) return

			staleShifts.forEach { it.endedAt = it.startedAt.plus(STALE_AFTER) }
			workShiftRepository.saveAll(staleShifts)

			log.warn(
				"Auto-closed {} stale work shift(s) open longer than {}h (inspectorIds={})",
				staleShifts.size,
				STALE_AFTER.toHours(),
				staleShifts.map { it.inspectorId }
			)
		} catch (e: Exception) {
			log.error("WorkShift cleanup failed", e)
		}
	}

	companion object {
		private const val ONE_HOUR_MILLIS = 60L * 60 * 1000
		val STALE_AFTER: Duration = Duration.ofHours(16)
	}
}

package uz.safecity.transportobserver.common.util

import java.time.Duration
import java.time.Instant

/**
 * Shared "is this signal still fresh" window for every online/presence computation in the app —
 * both the mobile GPS-heartbeat signal
 * ([uz.safecity.transportobserver.map.entity.InspectorLocation.updatedAt]) and the
 * any-platform session-activity signal
 * ([uz.safecity.transportobserver.auth.entity.Account.lastActiveAt], stamped by
 * [uz.safecity.transportobserver.auth.security.PresenceTracker]). Kept as one constant so the
 * "so'nggi 5 daqiqa" MVP window can't drift between call sites
 * ([uz.safecity.transportobserver.map.service.InspectorLocationService],
 * [uz.safecity.transportobserver.employees.service.EmployeeService]).
 */
object PresenceUtils {

	val ONLINE_WINDOW: Duration = Duration.ofMinutes(5)

	fun isRecent(instant: Instant?, now: Instant = Instant.now()): Boolean =
		instant != null && Duration.between(instant, now) <= ONLINE_WINDOW

	/** The more recent of two possibly-null instants, or null if both are null. */
	fun latestOf(a: Instant?, b: Instant?): Instant? = when {
		a == null -> b
		b == null -> a
		a.isAfter(b) -> a
		else -> b
	}
}

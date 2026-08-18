package uz.safecity.transportobserver.auth.security

import uz.safecity.transportobserver.auth.repository.AccountRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Stamps [uz.safecity.transportobserver.auth.entity.Account.lastActiveAt] on every
 * successfully-authenticated request, from ANY platform (web panel or mobile app) and ANY role
 * — this is what lets a web-only login (no GPS heartbeat ever sent) still show up as "online" on
 * the admin map/employee list, per the task's "webdan hodim kirsa ... admin xaritada online
 * ko'rinishi kerak" requirement. Called from [JwtAuthenticationFilter].
 *
 * Writing to the DB on literally every request would be wasteful — an active web session alone
 * can be tens of requests/minute — for a value only ever read at [uz.safecity.transportobserver.common.util.PresenceUtils.ONLINE_WINDOW]
 * (5-minute) granularity. Writes are therefore throttled per-account to at most once every
 * [THROTTLE], tracked via an in-process [ConcurrentHashMap]:
 * - Memory footprint is bounded by the number of distinct *recently*-active accounts (HRM
 *   staff-roster scale, not "every account ever created"), since untouched entries are simply
 *   never added and old ones are naturally superseded, not accumulated per-request.
 * - A cold/missing cache entry (fresh deploy, or — in a multi-instance deployment — a request
 *   landing on an instance that hasn't seen this account recently) only costs one extra DB write;
 *   it is never a correctness problem, only a rare redundant write.
 * - The actual write is a single-column `UPDATE ... WHERE id = :id`
 *   ([uz.safecity.transportobserver.auth.repository.AccountRepository.updateLastActiveAt]), not a
 *   find-then-save, so even an untimely write is cheap (indexed PK match, no entity load).
 */
@Component
class PresenceTracker(
	private val accountRepository: AccountRepository
) {

	private val lastTouchedAt = ConcurrentHashMap<UUID, Instant>()

	@Transactional
	fun touch(accountId: UUID) {
		val now = Instant.now()
		val previouslyTouchedAt = lastTouchedAt[accountId]
		if (previouslyTouchedAt != null && Duration.between(previouslyTouchedAt, now) < THROTTLE) {
			return
		}
		// DB write BEFORE the in-memory cache is updated: if updateLastActiveAt throws (DB
		// hiccup), the cache entry must not be recorded as "just written" — otherwise this
		// account would be silently skipped for the rest of the throttle window even though
		// the write never actually happened. Caller (JwtAuthenticationFilter) treats this as
		// best-effort and swallows the exception.
		accountRepository.updateLastActiveAt(accountId, now)
		lastTouchedAt[accountId] = now
	}

	companion object {
		private val THROTTLE: Duration = Duration.ofSeconds(60)
	}
}

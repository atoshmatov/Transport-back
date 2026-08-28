package uz.safecity.transportobserver.auth.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Redis-backed opaque refresh tokens.
 *
 * Keys:
 *  - `refresh:{token}` -> accountId          (lookup + TTL, deleted on rotate/logout)
 *  - `refresh:acct:{accountId}` -> set of live tokens (so we can revoke ALL
 *    of a user's sessions when their account is blocked/deleted, per TZ)
 *  - `session:{token}` -> hash {platform, device, createdAt, lastUsedAt} — human-facing metadata
 *    for the "Faol sessiyalar" (Active Sessions) list in the admin panel's Profile > Faoliyat tab
 *    (see [uz.safecity.transportobserver.auth.dto.SessionDto]). Always issued/rotated/revoked in
 *    lockstep with the refresh token it describes (same TTL, same key lifecycle) so it can never
 *    outlive — or be orphaned from — the token itself. A token issued before this feature existed
 *    simply has no `session:{token}` hash; [listSessions] silently drops those (see its kdoc)
 *    rather than fabricating metadata for them.
 */
@Service
class RefreshTokenService(
	private val redisTemplate: RedisTemplate<String, String>,
	@Value("\${security.jwt.refresh-token-ttl-seconds}") private val refreshTokenTtlSeconds: Long
) {

	companion object {
		/**
		 * 64 bits (16 hex chars) of a SHA-256 digest is plenty to make an accidental collision
		 * between two live sessions of the SAME account astronomically unlikely (that's the only
		 * scope that matters — see kdoc below), while keeping the id short enough to be a
		 * reasonable REST path segment (`DELETE /api/v1/auth/sessions/{id}`).
		 */
		private const val SESSION_ID_LENGTH = 16

		/**
		 * Public, one-way, non-reversible identifier for a refresh token — this is what
		 * [uz.safecity.transportobserver.auth.dto.SessionDto.id] actually carries. The raw refresh
		 * token itself must NEVER leave the server in an API response (it IS the bearer credential
		 * for `/auth/refresh`), so the list/revoke endpoints hand the client this hash instead.
		 *
		 * Going id -> token is deliberately NOT done via a reverse Redis lookup (e.g. an
		 * `idmap:{id} -> token` key) — instead [findTokenBySessionId] scans only the CALLER's own
		 * `refresh:acct:{accountId}` set and re-hashes each candidate. That single design choice is
		 * also exactly the ownership check `DELETE /sessions/{id}` needs: a session id computed
		 * from another account's token can never match anything in a different account's set, so
		 * there is no separate "does this session belong to me" check to forget.
		 */
		fun hashSessionId(token: String): String {
			val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
			return digest.joinToString("") { "%02x".format(it) }.take(SESSION_ID_LENGTH)
		}
	}

	/** One live session's metadata, keyed by its own (still-raw, server-side-only) [token]. */
	data class SessionInfo(
		val token: String,
		val platform: String,
		val device: String?,
		val createdAt: Instant,
		val lastUsedAt: Instant
	)

	private fun tokenKey(token: String) = "refresh:$token"
	private fun accountIndexKey(accountId: UUID) = "refresh:acct:$accountId"
	private fun sessionKey(token: String) = "session:$token"

	/** [platform]/[device] — see [SessionDevice] — default to a generic web session for callers that don't care (tests, internal use). */
	fun issue(accountId: UUID, platform: String = "WEB", device: String? = null): String {
		val token = createToken(accountId)
		val now = Instant.now()
		writeSessionMetadata(token, platform = platform, device = device, createdAt = now, lastUsedAt = now)
		return token
	}

	fun resolveAccountId(token: String): UUID? =
		redisTemplate.opsForValue().get(tokenKey(token))?.let { UUID.fromString(it) }

	fun revoke(token: String) {
		val accountId = resolveAccountId(token)
		redisTemplate.delete(tokenKey(token))
		redisTemplate.delete(sessionKey(token))
		if (accountId != null) {
			redisTemplate.opsForSet().remove(accountIndexKey(accountId), token)
		}
	}

	/** Revoke every refresh token issued to this account (block/delete/password reset by admin). */
	fun revokeAllForAccount(accountId: UUID) {
		val tokens = redisTemplate.opsForSet().members(accountIndexKey(accountId)) ?: emptySet()
		tokens.forEach {
			redisTemplate.delete(tokenKey(it))
			redisTemplate.delete(sessionKey(it))
		}
		redisTemplate.delete(accountIndexKey(accountId))
	}

	/**
	 * Rotate: issue a new token and invalidate the old one. The new token's session metadata
	 * CARRIES FORWARD the old token's `platform`/`device`/`createdAt` (a refresh is a continuation
	 * of the same session, not a new one — the "Faol sessiyalar" list should keep showing the
	 * original login time and device across many silent token refreshes), only bumping
	 * `lastUsedAt` to now. [fallbackPlatform]/[fallbackDevice] are used only when the old token
	 * had no session metadata at all (pre-existing token from before this feature shipped).
	 */
	fun rotate(oldToken: String, accountId: UUID, fallbackPlatform: String = "WEB", fallbackDevice: String? = null): String {
		val previous = readSessionMetadata(oldToken)
		revoke(oldToken)
		val newToken = createToken(accountId)
		val now = Instant.now()
		writeSessionMetadata(
			newToken,
			platform = previous?.platform ?: fallbackPlatform,
			device = previous?.device ?: fallbackDevice,
			createdAt = previous?.createdAt ?: now,
			lastUsedAt = now
		)
		return newToken
	}

	/**
	 * All live sessions for [accountId], newest-activity-first is NOT applied here (callers sort
	 * as needed) — see [SessionDto] for the response shape this feeds. Tokens whose `session:{token}`
	 * hash is missing (see class kdoc) are silently skipped rather than surfaced as broken rows.
	 */
	fun listSessions(accountId: UUID): List<SessionInfo> {
		val tokens = redisTemplate.opsForSet().members(accountIndexKey(accountId)) ?: emptySet()
		return tokens.mapNotNull { readSessionMetadata(it) }
	}

	/** See [findTokenBySessionId] kdoc — the account-scoped scan IS the ownership check. */
	fun findTokenBySessionId(accountId: UUID, sessionId: String): String? {
		val tokens = redisTemplate.opsForSet().members(accountIndexKey(accountId)) ?: emptySet()
		return tokens.firstOrNull { hashSessionId(it) == sessionId }
	}

	/**
	 * Revokes the session identified by [sessionId] only if it belongs to [accountId]. Returns
	 * `false` (a no-op) when [sessionId] doesn't resolve within that account's own token set —
	 * either because it never existed or because it belongs to a DIFFERENT account — so the
	 * controller can turn that into a plain 404 without distinguishing the two cases and thereby
	 * leaking whether a given id exists on someone else's account.
	 */
	fun revokeSessionForAccount(accountId: UUID, sessionId: String): Boolean {
		val token = findTokenBySessionId(accountId, sessionId) ?: return false
		revoke(token)
		return true
	}

	/** Token creation + TTL/index bookkeeping only — no session metadata (both [issue] and [rotate] add that themselves). */
	private fun createToken(accountId: UUID): String {
		val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
		val ttl = Duration.ofSeconds(refreshTokenTtlSeconds)
		redisTemplate.opsForValue().set(tokenKey(token), accountId.toString(), ttl)
		redisTemplate.opsForSet().add(accountIndexKey(accountId), token)
		redisTemplate.expire(accountIndexKey(accountId), ttl)
		return token
	}

	private fun writeSessionMetadata(token: String, platform: String, device: String?, createdAt: Instant, lastUsedAt: Instant) {
		val fields = mutableMapOf(
			"platform" to platform,
			"createdAt" to createdAt.toString(),
			"lastUsedAt" to lastUsedAt.toString()
		)
		device?.let { fields["device"] = it }
		val key = sessionKey(token)
		redisTemplate.opsForHash<String, String>().putAll(key, fields)
		// Same TTL as the refresh token it describes (class kdoc) — must be re-applied on every
		// write since HSET/HMSET on an already-existing hash does not preserve/extend a prior EXPIRE.
		redisTemplate.expire(key, Duration.ofSeconds(refreshTokenTtlSeconds))
	}

	private fun readSessionMetadata(token: String): SessionInfo? {
		val entries = redisTemplate.opsForHash<String, String>().entries(sessionKey(token))
		if (entries.isEmpty()) return null
		val platform = entries["platform"] ?: return null
		val createdAt = entries["createdAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
		val lastUsedAt = entries["lastUsedAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: createdAt
		return SessionInfo(token = token, platform = platform, device = entries["device"], createdAt = createdAt, lastUsedAt = lastUsedAt)
	}
}

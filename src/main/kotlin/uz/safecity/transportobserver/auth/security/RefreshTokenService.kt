package uz.safecity.transportobserver.auth.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * Redis-backed opaque refresh tokens.
 *
 * Keys:
 *  - `refresh:{token}` -> accountId          (lookup + TTL, deleted on rotate/logout)
 *  - `refresh:acct:{accountId}` -> set of live tokens (so we can revoke ALL
 *    of a user's sessions when their account is blocked/deleted, per TZ)
 */
@Service
class RefreshTokenService(
	private val redisTemplate: RedisTemplate<String, String>,
	@Value("\${security.jwt.refresh-token-ttl-seconds}") private val refreshTokenTtlSeconds: Long
) {

	private fun tokenKey(token: String) = "refresh:$token"
	private fun accountIndexKey(accountId: UUID) = "refresh:acct:$accountId"

	fun issue(accountId: UUID): String {
		val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
		val ttl = Duration.ofSeconds(refreshTokenTtlSeconds)
		redisTemplate.opsForValue().set(tokenKey(token), accountId.toString(), ttl)
		redisTemplate.opsForSet().add(accountIndexKey(accountId), token)
		redisTemplate.expire(accountIndexKey(accountId), ttl)
		return token
	}

	fun resolveAccountId(token: String): UUID? =
		redisTemplate.opsForValue().get(tokenKey(token))?.let { UUID.fromString(it) }

	fun revoke(token: String) {
		val accountId = resolveAccountId(token)
		redisTemplate.delete(tokenKey(token))
		if (accountId != null) {
			redisTemplate.opsForSet().remove(accountIndexKey(accountId), token)
		}
	}

	/** Revoke every refresh token issued to this account (block/delete/password reset by admin). */
	fun revokeAllForAccount(accountId: UUID) {
		val tokens = redisTemplate.opsForSet().members(accountIndexKey(accountId)) ?: emptySet()
		tokens.forEach { redisTemplate.delete(tokenKey(it)) }
		redisTemplate.delete(accountIndexKey(accountId))
	}

	/** Rotate: issue a new token and invalidate the old one. */
	fun rotate(oldToken: String, accountId: UUID): String {
		revoke(oldToken)
		return issue(accountId)
	}
}

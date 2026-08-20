package uz.safecity.transportobserver.auth.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * IP-level brute-force guard for `POST /api/v1/auth/login`, sitting alongside the existing
 * per-account lockout ([uz.safecity.transportobserver.auth.service.AuthService.login] via
 * `security.lockout.*`). The per-account lockout alone doesn't stop a single IP from cycling
 * through many different usernames — this closes that gap independently of which account is
 * being tried.
 *
 * MVP fixed-window counter backed by Redis (already a dependency here for
 * [RefreshTokenService], so no new infra): `INCR` a per-IP key, and on the first hit in a window
 * set a TTL equal to the window length. Once the counter exceeds [maxAttempts] within that TTL,
 * every further call for that IP is rejected until the key expires and the window resets.
 *
 * This is a *fixed* window, not a true sliding one — a burst can land up to ~2x [maxAttempts]
 * requests across a window boundary (e.g. 10 at 0:59 and 10 more at 1:01). That's an accepted
 * trade-off for an MVP: it still turns an unbounded brute-force loop into a bounded one, and
 * `INCR`+`EXPIRE` is atomic enough per-key that concurrent requests from the same IP can't race
 * past the limit. Bucket4j is not on the classpath in this project (checked before choosing this
 * approach), so no new dependency was added for it.
 */
@Service
class LoginRateLimiter(
	private val redisTemplate: RedisTemplate<String, String>,
	@Value("\${security.rate-limit.login.max-attempts:10}") private val maxAttempts: Long,
	@Value("\${security.rate-limit.login.window-seconds:60}") private val windowSeconds: Long
) {

	private fun key(ip: String) = "login-ratelimit:$ip"

	/**
	 * Records one login attempt from [ip] and returns `true` if it's allowed to proceed, `false`
	 * if [ip] has already used up its budget for the current window.
	 */
	fun tryAcquire(ip: String): Boolean {
		val redisKey = key(ip)
		val count = redisTemplate.opsForValue().increment(redisKey) ?: 1L
		if (count == 1L) {
			// Only the request that created the key sets its TTL — every later INCR within the
			// same window must NOT reset the expiry, or a steady stream of requests could keep
			// pushing the window forward forever and never actually reset.
			redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds))
		}
		return count <= maxAttempts
	}
}

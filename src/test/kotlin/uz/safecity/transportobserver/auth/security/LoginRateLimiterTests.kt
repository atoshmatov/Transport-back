package uz.safecity.transportobserver.auth.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.util.UUID

/**
 * Covers the O'RTA-YUQORI gap flagged by the security audit: [AuthService][uz.safecity.transportobserver.auth.service.AuthService.login]
 * only had a per-account lockout, so a single IP could still brute-force many different
 * usernames unrestricted. [LoginRateLimiter] is the new IP-level budget backing
 * [LoginRateLimitFilter], wired ahead of [JwtAuthenticationFilter] in [SecurityConfig].
 *
 * Uses the real Redis instance from docker-compose (same infra every other Redis-backed test in
 * this codebase, e.g. [uz.safecity.transportobserver.auth.security.RefreshTokenService]'s
 * production usage, implicitly relies on) rather than mocking [RedisTemplate] — the behavior under
 * test (atomic INCR + first-hit TTL) is exactly the kind of thing a mock would get subtly wrong.
 * A random per-test IP-like key avoids cross-test interference without needing to flush Redis.
 */
@SpringBootTest
class LoginRateLimiterTests {

	@Autowired
	lateinit var loginRateLimiter: LoginRateLimiter

	@Autowired
	lateinit var redisTemplate: RedisTemplate<String, String>

	private val usedIps = mutableListOf<String>()

	private fun testIp(): String = "203.0.113.${(1..254).random()}-${UUID.randomUUID()}".also { usedIps += it }

	@AfterEach
	fun cleanup() {
		usedIps.forEach { redisTemplate.delete("login-ratelimit:$it") }
		usedIps.clear()
	}

	@Test
	fun `allows attempts up to the configured max for a fresh IP`() {
		val ip = testIp()

		// application.yml default: security.rate-limit.login.max-attempts=10
		repeat(10) { attempt ->
			assertTrue(loginRateLimiter.tryAcquire(ip), "Attempt #${attempt + 1} should still be allowed")
		}
	}

	@Test
	fun `rejects once an IP exceeds the configured max within the window`() {
		val ip = testIp()

		repeat(10) { loginRateLimiter.tryAcquire(ip) }

		assertFalse(loginRateLimiter.tryAcquire(ip), "The 11th attempt within the same window must be rejected")
		assertFalse(loginRateLimiter.tryAcquire(ip), "Further attempts stay rejected until the window expires")
	}

	@Test
	fun `different IPs are tracked independently`() {
		val ipA = testIp()
		val ipB = testIp()

		repeat(10) { loginRateLimiter.tryAcquire(ipA) }

		assertFalse(loginRateLimiter.tryAcquire(ipA), "ipA is exhausted")
		assertTrue(loginRateLimiter.tryAcquire(ipB), "ipB must have its own independent budget")
	}
}

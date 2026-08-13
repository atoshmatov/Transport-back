package uz.safecity.transportobserver.auth.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.Key
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Issues and validates the short-lived JWT *access* token. The *refresh*
 * token is a random opaque value tracked in Redis by [RefreshTokenService] —
 * not a JWT — so it can be revoked instantly (required by TZ: revoke on
 * block/delete of an account).
 */
@Component
class JwtService(
	@Value("\${security.jwt.secret}") secret: String,
	@Value("\${security.jwt.access-token-ttl-seconds}") private val accessTokenTtlSeconds: Long
) {

	private val key: Key = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
	private val log = LoggerFactory.getLogger(JwtService::class.java)

	fun accessTokenTtlSeconds(): Long = accessTokenTtlSeconds

	fun generateAccessToken(accountId: UUID, username: String, role: String): String {
		val now = Instant.now()
		return Jwts.builder()
			.subject(accountId.toString())
			.claim("username", username)
			.claim("role", role)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
			.signWith(key)
			.compact()
	}

	// Deliberately fail-closed: any parse/verify problem (expired, bad signature,
	// malformed, ...) returns null, same as "no token" to every caller — this
	// behavior must NOT change. The WARN log below is purely diagnostic (e.g. to
	// notice a clock-skew or a client sending garbage) — expired tokens log at
	// DEBUG since those are routine, not something to page on.
	fun parse(token: String): Claims? = try {
		Jwts.parser().verifyWith(key as javax.crypto.SecretKey).build()
			.parseSignedClaims(token).payload
	} catch (ex: ExpiredJwtException) {
		log.debug("JWT rejected: expired ({})", ex.message)
		null
	} catch (ex: Exception) {
		log.warn("JWT rejected: {} - {}", ex.javaClass.simpleName, ex.message)
		null
	}

	fun extractAccountId(claims: Claims): UUID = UUID.fromString(claims.subject)
}

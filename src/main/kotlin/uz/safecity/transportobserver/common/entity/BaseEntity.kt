package uz.safecity.transportobserver.common.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Common audit fields shared by every domain entity (auth, employees, incidents, ...).
 * Uses UUID primary keys so IDs are safe to expose in the public API and to
 * generate client-side (mobile clients, offline-first flows) without a DB round-trip.
 *
 * [version] backs JPA optimistic locking: any find-then-modify-then-save flow
 * across every entity gets a lost-update guard for free (a stale save throws
 * `OptimisticLockingFailureException` instead of silently overwriting a
 * concurrent change). High-contention counters (e.g. Account.failedAttempts)
 * should still prefer an atomic `UPDATE ... SET x = x + 1` query over
 * find-then-save, since optimistic locking only detects the conflict — it
 * doesn't prevent the retry storm under heavy concurrent writes.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(updatable = false, nullable = false)
	val id: java.util.UUID? = null,

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant? = null,

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant? = null
) {
	@Version
	@Column(name = "version", nullable = false)
	var version: Long? = null
}

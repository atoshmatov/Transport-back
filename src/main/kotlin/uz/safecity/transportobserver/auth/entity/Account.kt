package uz.safecity.transportobserver.auth.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A login account. Per TZ: no self-registration — accounts are created only
 * by an admin (see AuthService/EmployeeService account-provisioning flow).
 *
 * Login is username+password only, no OTP.
 */
@Entity
@Table(name = "accounts")
class Account(

	@Column(nullable = false, unique = true, length = 64)
	var username: String,

	@Column(name = "password_hash", nullable = false)
	var passwordHash: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var role: RoleType,

	/** Employee this account belongs to, if any (nullable: e.g. a SUPER_ADMIN bootstrap account). */
	@Column(name = "employee_id")
	var employeeId: UUID? = null,

	@Column(name = "must_change_password", nullable = false)
	var mustChangePassword: Boolean = true,

	@Column(name = "is_active", nullable = false)
	var isActive: Boolean = true,

	@Column(name = "failed_attempts", nullable = false)
	var failedAttempts: Int = 0,

	@Column(name = "locked_until")
	var lockedUntil: Instant? = null,

	@Column(name = "created_by")
	var createdBy: UUID? = null,

	/**
	 * Last time this account made a successfully-authenticated request, from ANY platform
	 * (web panel or mobile app) — stamped by [uz.safecity.transportobserver.auth.security.PresenceTracker]
	 * from [uz.safecity.transportobserver.auth.security.JwtAuthenticationFilter] on every
	 * request, throttled to at most once/minute per account (see that class's kdoc). This is
	 * the "session activity" online signal, distinct from (and combined with, for INSPECTOR)
	 * the GPS-heartbeat-based [uz.safecity.transportobserver.map.entity.InspectorLocation.updatedAt]
	 * signal — see [uz.safecity.transportobserver.map.service.InspectorLocationService] kdoc.
	 */
	@Column(name = "last_active_at")
	var lastActiveAt: Instant? = null

) : BaseEntity() {

	fun isCurrentlyLocked(): Boolean = lockedUntil?.isAfter(Instant.now()) == true

	// NOTE: failed-attempt counting and lockout are applied via
	// AccountRepository's atomic `UPDATE ... SET failed_attempts = failed_attempts + 1`
	// queries (see AuthService.login), not via a find-then-save mutation on this
	// entity — that pattern lost updates under concurrent bad-login attempts.
}

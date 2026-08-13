package uz.safecity.transportobserver.auth.repository

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
	fun findByUsername(username: String): Optional<Account>
	fun existsByUsername(username: String): Boolean
	fun findByEmployeeId(employeeId: UUID): Optional<Account>

	/** Batch lookup used to enrich a page of [uz.safecity.transportobserver.employees.entity.Employee] rows with account info in one query. */
	fun findByEmployeeIdIn(employeeIds: Collection<UUID>): List<Account>

	// --- Employee-list filtering (role/isActive live on Account, not Employee) ---
	fun findByRole(role: RoleType): List<Account>
	fun findByIsActive(isActive: Boolean): List<Account>
	fun findByRoleAndIsActive(role: RoleType, isActive: Boolean): List<Account>

	/**
	 * Reports dashboard `activeEmployeesCount` (ReportService). Counts *accounts*, not Employee
	 * rows directly — equivalent in practice because every Employee-provisioned Account is 1:1
	 * with its Employee (see EmployeeService#create, which always sets `employeeId` on creation),
	 * and there is no mapped JPA relation between Account and Employee to join through instead
	 * (Account.employeeId is a plain FK column, see Account kdoc). `employeeId is not null`
	 * excludes non-employee accounts (e.g. the SUPER_ADMIN bootstrap account) from the count.
	 */
	fun countByIsActiveTrueAndEmployeeIdIsNotNull(): Long

	/** Reports dashboard `blockedAccountsCount` (ReportService) — call with `false`. */
	fun countByIsActive(isActive: Boolean): Long

	/**
	 * Atomic `failed_attempts = failed_attempts + 1` at the DB row level.
	 * Deliberately bypasses the usual find-then-save flow: under concurrent
	 * bad-password attempts, two transactions loading the same in-memory
	 * `failedAttempts` value and saving it back would each overwrite the
	 * other's increment ("lost update"), undercounting attempts and letting
	 * lockout be delayed or skipped entirely. A single `UPDATE` is atomic
	 * per row in Postgres, so no increment is ever lost regardless of
	 * concurrency.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Account a set a.failedAttempts = a.failedAttempts + 1 where a.id = :id")
	fun incrementFailedAttempts(@Param("id") id: UUID): Int

	/** Locks the account only if the threshold has actually been reached (checked in the same statement). */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Account a set a.lockedUntil = :lockedUntil where a.id = :id and a.failedAttempts >= :maxAttempts")
	fun lockIfThresholdReached(
		@Param("id") id: UUID,
		@Param("maxAttempts") maxAttempts: Int,
		@Param("lockedUntil") lockedUntil: Instant
	): Int

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Account a set a.failedAttempts = 0, a.lockedUntil = null where a.id = :id")
	fun resetFailedAttempts(@Param("id") id: UUID): Int
}

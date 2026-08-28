package uz.safecity.transportobserver.employees.dto

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.common.util.PresenceUtils
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.entity.EmployeePositionHistory
import uz.safecity.transportobserver.employees.entity.EmployeeStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * [account] is nullable only to tolerate legacy rows created before this
 * module had account provisioning (pre-TASK-583 skeleton `create()` calls).
 * Every employee created via [CreateEmployeeRequest] going forward always has
 * a linked account. The password hash is never included here, under any
 * circumstance — see [CreateEmployeeResponse.temporaryPassword] for the one
 * place a plaintext credential is ever returned.
 */
data class EmployeeDto(
	val id: UUID,
	val fullName: String,
	val position: String?,
	val department: String?,
	val regionName: String?,
	val phoneNumber: String?,
	val status: EmployeeStatus,
	val hiredAt: LocalDate?,
	val accountId: UUID?,
	val username: String?,
	val role: RoleType?,
	val isActive: Boolean?,
	val mustChangePassword: Boolean?,
	val photoUrl: String? = null,
	/**
	 * Session-activity presence — true iff [Account.lastActiveAt] (stamped by
	 * [uz.safecity.transportobserver.auth.security.PresenceTracker] on every authenticated
	 * request, any role, web or mobile) is within [PresenceUtils.ONLINE_WINDOW]. Unlike the
	 * map's `GET /api/v1/map/employees` (which additionally factors in the INSPECTOR-only GPS
	 * heartbeat, see [uz.safecity.transportobserver.map.service.InspectorLocationService]
	 * kdoc), this roster endpoint covers every role, so it deliberately only uses the
	 * session-activity signal — there is no location to speak of for an ADMIN/OPERATOR account.
	 * `null` when there's no linked account at all (legacy row, see class kdoc above).
	 */
	val online: Boolean?,
	val lastActiveAt: Instant?,
	/**
	 * `true` iff this employee is an INSPECTOR with a currently open
	 * [uz.safecity.transportobserver.shifts.entity.WorkShift] (explicit "ishga chiqdim" check-in via
	 * `POST /api/v1/inspector/me/shift/start`) — see that entity's kdoc for why this is a DIFFERENT,
	 * independent signal from [online] (session-activity presence). Always `false` for a
	 * non-INSPECTOR role or a legacy row with no linked account, never `null` — unlike [online]
	 * there is no "no signal to speak of" case: an employee either currently has an open shift or
	 * they don't.
	 */
	val onDuty: Boolean,
	// --- HR "full profile" fields — see Employee kdoc. All nullable: `null` = not entered yet. ---
	val personalId: String?,
	val birthDate: LocalDate?,
	val homeAddress: String?,
	val email: String?,
	val serviceCertificateNumber: String?,
	val serviceCertificateExpiresAt: LocalDate?,
	val driverLicenseCategory: String?,
	val lastCertificationAt: LocalDate?,
	val assignedTabletId: String?,
	val assignedBadgeCameraId: String?
) {
	companion object {
		fun from(employee: Employee, account: Account?, photoUrl: String? = null, onDuty: Boolean = false) = EmployeeDto(
			id = requireNotNull(employee.id),
			fullName = employee.fullName,
			position = employee.position,
			department = employee.department,
			regionName = employee.regionName,
			phoneNumber = employee.phoneNumber,
			status = employee.status,
			hiredAt = employee.hiredAt,
			accountId = account?.id,
			username = account?.username,
			role = account?.role,
			isActive = account?.isActive,
			mustChangePassword = account?.mustChangePassword,
			photoUrl = photoUrl,
			online = account?.let { PresenceUtils.isRecent(it.lastActiveAt) },
			lastActiveAt = account?.lastActiveAt,
			onDuty = onDuty,
			personalId = employee.personalId,
			birthDate = employee.birthDate,
			homeAddress = employee.homeAddress,
			email = employee.email,
			serviceCertificateNumber = employee.serviceCertificateNumber,
			serviceCertificateExpiresAt = employee.serviceCertificateExpiresAt,
			driverLicenseCategory = employee.driverLicenseCategory,
			lastCertificationAt = employee.lastCertificationAt,
			assignedTabletId = employee.assignedTabletId,
			assignedBadgeCameraId = employee.assignedBadgeCameraId
		)
	}
}

data class CreateEmployeeRequest(
	@field:NotBlank(message = "F.I.Sh majburiy")
	@field:Size(max = 150, message = "F.I.Sh 150 belgidan oshmasligi kerak")
	val fullName: String,

	val position: String? = null,
	val department: String? = null,

	// TODO (region module): text field until a real `regions` table exists — see Employee kdoc.
	val regionName: String? = null,

	val phoneNumber: String? = null,
	val hiredAt: LocalDate? = null,

	@field:NotNull(message = "role majburiy")
	val role: RoleType? = null,

	/** Optional — if blank/omitted, a username is auto-generated from [fullName]. */
	val username: String? = null,

	// --- HR "full profile" fields — all optional, see Employee kdoc. ---
	@field:Size(max = 32, message = "JSHSHIR/pasport 32 belgidan oshmasligi kerak")
	val personalId: String? = null,
	val birthDate: LocalDate? = null,
	val homeAddress: String? = null,
	@field:Email(message = "email formati noto'g'ri")
	@field:Size(max = 150, message = "email 150 belgidan oshmasligi kerak")
	val email: String? = null,
	@field:Size(max = 64, message = "guvohnoma raqami 64 belgidan oshmasligi kerak")
	val serviceCertificateNumber: String? = null,
	val serviceCertificateExpiresAt: LocalDate? = null,
	@field:Size(max = 32, message = "toifa 32 belgidan oshmasligi kerak")
	val driverLicenseCategory: String? = null,
	val lastCertificationAt: LocalDate? = null,
	@field:Size(max = 64, message = "planshet identifikatori 64 belgidan oshmasligi kerak")
	val assignedTabletId: String? = null,
	@field:Size(max = 64, message = "badge-camera identifikatori 64 belgidan oshmasligi kerak")
	val assignedBadgeCameraId: String? = null
)

/**
 * Returned only from `POST /api/v1/admin/employees` (and `POST /{id}/reset-password`).
 * [temporaryPassword] is shown to the admin exactly once so it can be handed to
 * the employee out-of-band — it is never stored in plaintext and never appears
 * in any GET response.
 */
data class CreateEmployeeResponse(
	val employee: EmployeeDto,
	val temporaryPassword: String
)

/** Role and password are intentionally excluded — changed via dedicated endpoints only. */
data class UpdateEmployeeRequest(
	@field:NotBlank(message = "F.I.Sh majburiy")
	val fullName: String,

	val position: String? = null,
	val department: String? = null,
	val regionName: String? = null,
	val phoneNumber: String? = null,
	val hiredAt: LocalDate? = null,

	// --- HR "full profile" fields — all optional, see Employee/CreateEmployeeRequest kdoc. ---
	@field:Size(max = 32, message = "JSHSHIR/pasport 32 belgidan oshmasligi kerak")
	val personalId: String? = null,
	val birthDate: LocalDate? = null,
	val homeAddress: String? = null,
	@field:Email(message = "email formati noto'g'ri")
	@field:Size(max = 150, message = "email 150 belgidan oshmasligi kerak")
	val email: String? = null,
	@field:Size(max = 64, message = "guvohnoma raqami 64 belgidan oshmasligi kerak")
	val serviceCertificateNumber: String? = null,
	val serviceCertificateExpiresAt: LocalDate? = null,
	@field:Size(max = 32, message = "toifa 32 belgidan oshmasligi kerak")
	val driverLicenseCategory: String? = null,
	val lastCertificationAt: LocalDate? = null,
	@field:Size(max = 64, message = "planshet identifikatori 64 belgidan oshmasligi kerak")
	val assignedTabletId: String? = null,
	@field:Size(max = 64, message = "badge-camera identifikatori 64 belgidan oshmasligi kerak")
	val assignedBadgeCameraId: String? = null
)

data class UpdateEmployeeStatusRequest(
	@field:NotNull(message = "isActive majburiy")
	val isActive: Boolean? = null
)

/** `GET /api/v1/admin/employees/{id}/position-history` row — see [EmployeePositionHistory] kdoc. */
data class EmployeePositionHistoryDto(
	val id: UUID,
	val employeeId: UUID,
	val position: String,
	val regionName: String?,
	val startedAt: Instant,
	/** `null` = this is the employee's current spell. */
	val endedAt: Instant?
) {
	companion object {
		fun from(entity: EmployeePositionHistory) = EmployeePositionHistoryDto(
			id = requireNotNull(entity.id),
			employeeId = entity.employeeId,
			position = entity.position,
			regionName = entity.regionName,
			startedAt = entity.startedAt,
			endedAt = entity.endedAt
		)
	}
}

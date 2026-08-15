package uz.safecity.transportobserver.employees.dto

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.entity.EmployeeStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
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
	val photoUrl: String? = null
) {
	companion object {
		fun from(employee: Employee, account: Account?, photoUrl: String? = null) = EmployeeDto(
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
			photoUrl = photoUrl
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
	val username: String? = null
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
	val hiredAt: LocalDate? = null
)

data class UpdateEmployeeStatusRequest(
	@field:NotNull(message = "isActive majburiy")
	val isActive: Boolean? = null
)

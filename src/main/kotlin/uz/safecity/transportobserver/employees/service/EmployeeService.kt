package uz.safecity.transportobserver.employees.service

import uz.safecity.transportobserver.audit.service.AuditService
import uz.safecity.transportobserver.auth.dto.ResetPasswordResponse
import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.RoleHierarchyGuard
import uz.safecity.transportobserver.auth.security.TemporaryPasswordGenerator
import uz.safecity.transportobserver.auth.service.AuthService
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.storage.FileStorageService
import org.springframework.web.multipart.MultipartFile
import uz.safecity.transportobserver.employees.dto.CreateEmployeeRequest
import uz.safecity.transportobserver.employees.dto.CreateEmployeeResponse
import uz.safecity.transportobserver.employees.dto.EmployeeDto
import uz.safecity.transportobserver.employees.dto.EmployeePositionHistoryDto
import uz.safecity.transportobserver.employees.dto.UpdateEmployeeRequest
import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.employees.entity.EmployeeStatus
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.shifts.service.WorkShiftService
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EmployeeService(
	private val employeeRepository: EmployeeRepository,
	private val accountRepository: AccountRepository,
	private val passwordEncoder: PasswordEncoder,
	private val temporaryPasswordGenerator: TemporaryPasswordGenerator,
	private val authService: AuthService,
	private val auditService: AuditService,
	private val fileStorageService: FileStorageService,
	private val workShiftService: WorkShiftService,
	private val employeePositionHistoryService: EmployeePositionHistoryService
) {

	fun list(
		regionName: String?,
		role: RoleType?,
		isActive: Boolean?,
		status: EmployeeStatus?,
		pageable: Pageable
	): PageResponse<EmployeeDto> {
		// role/isActive live on Account, not Employee — resolve them to a set of
		// employeeIds first, then filter Employee by that set alongside its own
		// columns. Fine at HRM staff-roster scale; revisit with a real join
		// query if this table ever grows large.
		val employeeIdFilter: Set<UUID>? = when {
			role != null && isActive != null ->
				accountRepository.findByRoleAndIsActive(role, isActive).mapNotNull { it.employeeId }.toSet()
			role != null -> accountRepository.findByRole(role).mapNotNull { it.employeeId }.toSet()
			isActive != null -> accountRepository.findByIsActive(isActive).mapNotNull { it.employeeId }.toSet()
			else -> null
		}

		val page = employeeRepository.findAll(buildSpecification(regionName, status, employeeIdFilter), pageable)

		val accountsByEmployeeId = accountRepository.findByEmployeeIdIn(page.content.mapNotNull { it.id })
			.associateBy { it.employeeId }
		// Batched — one query for the whole page's onDuty flags, not one per row. Safe to pass every
		// account id (not just INSPECTOR ones): a non-INSPECTOR account simply never has a WorkShift
		// row, so it never comes back in the result — see WorkShiftService#onDutyInspectorIds kdoc.
		val onDutyIds = workShiftService.onDutyInspectorIds(accountsByEmployeeId.values.mapNotNull { it.id })

		return PageResponse(
			content = page.content.map {
				val account = accountsByEmployeeId[it.id]
				EmployeeDto.from(it, account, getPhotoUrl(it.photoKey), onDuty = account?.id in onDutyIds)
			},
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages
		)
	}

	fun getById(id: UUID): EmployeeDto {
		val employee = findEmployeeOrThrow(id)
		val account = accountRepository.findByEmployeeId(id).orElse(null)
		val onDuty = account?.id?.let { workShiftService.onDutyInspectorIds(listOf(it)).isNotEmpty() } ?: false
		return EmployeeDto.from(employee, account, getPhotoUrl(employee.photoKey), onDuty = onDuty)
	}

	/** Creates the [Employee] row and its linked [Account] together. See [CreateEmployeeResponse] kdoc re: the one-time password. */
	@Transactional
	fun create(request: CreateEmployeeRequest, actorAccountId: UUID?, actorRole: RoleType): CreateEmployeeResponse {
		val role = requireNotNull(request.role) { "role majburiy" }
		RoleHierarchyGuard.assertCanManage(actorRole, role)

		val username = request.username?.trim()?.takeIf { it.isNotBlank() } ?: generateUsername(request.fullName)
		if (accountRepository.existsByUsername(username)) {
			throw ConflictException("error.employee.username-taken", username)
		}

		val employee = employeeRepository.save(
			Employee(
				fullName = request.fullName,
				position = request.position,
				department = request.department,
				regionName = request.regionName,
				phoneNumber = request.phoneNumber,
				hiredAt = request.hiredAt,
				personalId = request.personalId,
				birthDate = request.birthDate,
				homeAddress = request.homeAddress,
				email = request.email,
				serviceCertificateNumber = request.serviceCertificateNumber,
				serviceCertificateExpiresAt = request.serviceCertificateExpiresAt,
				driverLicenseCategory = request.driverLicenseCategory,
				lastCertificationAt = request.lastCertificationAt,
				assignedTabletId = request.assignedTabletId,
				assignedBadgeCameraId = request.assignedBadgeCameraId
			)
		)
		// Seeds the employee's first/current position-history spell (no-op close step, since a
		// brand-new employee has no prior open row) — see EmployeePositionHistoryService kdoc.
		employeePositionHistoryService.recordChange(requireNotNull(employee.id), request.position, request.regionName)

		val temporaryPassword = temporaryPasswordGenerator.generate()
		val account = accountRepository.save(
			Account(
				username = username,
				passwordHash = passwordEncoder.encode(temporaryPassword),
				role = role,
				employeeId = employee.id,
				mustChangePassword = true,
				createdBy = actorAccountId
			)
		)

		auditService.record(
			actorAccountId = actorAccountId,
			action = "EMPLOYEE_CREATED",
			entityType = "Employee",
			entityId = employee.id,
			metadata = "username=$username;role=$role"
		)

		return CreateEmployeeResponse(
			employee = EmployeeDto.from(employee, account, getPhotoUrl(employee.photoKey)),
			temporaryPassword = temporaryPassword
		)
	}

	/**
	 * fullName/position/department/regionName/phoneNumber/hiredAt only — role and password
	 * change via dedicated endpoints. Guarded by the same [RoleHierarchyGuard.assertCanManage]
	 * hierarchy gate as [updateStatus]/[resetPassword]: an ADMIN must not be able to edit a
	 * SUPER_ADMIN's or another ADMIN's core profile fields just because no role/status is being
	 * touched here. If the employee has no linked [Account] (legacy row — see [Employee] kdoc),
	 * there is no role to gate on, so the check is skipped and the update proceeds.
	 *
	 * Whenever `position` or `regionName` actually changes, this also closes the employee's
	 * current [uz.safecity.transportobserver.employees.entity.EmployeePositionHistory] spell and
	 * opens a new one via [EmployeePositionHistoryService.recordChange] — see that method's kdoc.
	 * The comparison happens BEFORE the fields below are overwritten, since [Employee] is mutable.
	 */
	@Transactional
	fun update(id: UUID, request: UpdateEmployeeRequest, actorAccountId: UUID?, actorRole: RoleType): EmployeeDto {
		val employee = findEmployeeOrThrow(id)
		val account = accountRepository.findByEmployeeId(id).orElse(null)
		account?.let { RoleHierarchyGuard.assertCanManage(actorRole, it.role) }

		val positionOrRegionChanged = employee.position != request.position || employee.regionName != request.regionName

		employee.fullName = request.fullName
		employee.position = request.position
		employee.department = request.department
		employee.regionName = request.regionName
		employee.phoneNumber = request.phoneNumber
		employee.hiredAt = request.hiredAt
		employee.personalId = request.personalId
		employee.birthDate = request.birthDate
		employee.homeAddress = request.homeAddress
		employee.email = request.email
		employee.serviceCertificateNumber = request.serviceCertificateNumber
		employee.serviceCertificateExpiresAt = request.serviceCertificateExpiresAt
		employee.driverLicenseCategory = request.driverLicenseCategory
		employee.lastCertificationAt = request.lastCertificationAt
		employee.assignedTabletId = request.assignedTabletId
		employee.assignedBadgeCameraId = request.assignedBadgeCameraId
		val saved = employeeRepository.save(employee)

		if (positionOrRegionChanged) {
			employeePositionHistoryService.recordChange(id, request.position, request.regionName)
		}

		auditService.record(actorAccountId, "EMPLOYEE_UPDATED", "Employee", id)

		return EmployeeDto.from(saved, account, getPhotoUrl(saved.photoKey))
	}

	/** `GET /api/v1/admin/employees/{id}/position-history` — newest spell first. */
	fun getPositionHistory(id: UUID): List<EmployeePositionHistoryDto> {
		findEmployeeOrThrow(id)
		return employeePositionHistoryService.list(id)
	}

	/** Block/activate = flips Account.isActive. Blocking immediately revokes every live session (TASK-566 pattern). */
	@Transactional
	fun updateStatus(id: UUID, isActive: Boolean, actorAccountId: UUID?, actorRole: RoleType): EmployeeDto {
		val employee = findEmployeeOrThrow(id)
		val account = accountRepository.findByEmployeeId(id)
			.orElseThrow { ResourceNotFoundException("error.employee.account-not-found", id) }
		RoleHierarchyGuard.assertCanManage(actorRole, account.role)

		account.isActive = isActive
		accountRepository.save(account)

		if (!isActive) {
			authService.revokeAllSessions(requireNotNull(account.id))
		}

		auditService.record(
			actorAccountId = actorAccountId,
			action = if (isActive) "EMPLOYEE_ACTIVATED" else "EMPLOYEE_BLOCKED",
			entityType = "Employee",
			entityId = id
		)

		return EmployeeDto.from(employee, account, getPhotoUrl(employee.photoKey))
	}

	/**
	 * Delegates to [AuthService.resetPassword] so both admin-reset flows (here and
	 * `/auth/reset-password`) share one implementation. The hierarchy check is applied twice —
	 * once here (fail fast, keyed off the Employee) and again inside [AuthService.resetPassword]
	 * itself (keyed off the Account) — which is intentionally redundant defense-in-depth: the
	 * inner check is what actually closes the privilege-escalation gap for the *other* entry
	 * point ([uz.safecity.transportobserver.auth.controller.AuthController.resetPassword]), so it
	 * must not be skippable, and keeping the outer check here means this method still fails
	 * before touching [AuthService] at all if it's ever called with a mismatched actor/target.
	 *
	 * Deliberately records its OWN audit entry in addition to the "ACCOUNT_PASSWORD_RESET" one
	 * [AuthService.resetPassword] always writes — this is a considered choice, not an oversight:
	 * both entries describe the same real-world event but at different, independently useful
	 * granularity. This one is entityType="Employee"/entityId=<employee id>, matching every other
	 * audit entry this service writes (EMPLOYEE_CREATED/EMPLOYEE_UPDATED/EMPLOYEE_BLOCKED/...), so
	 * "show me everything that happened to employee X" queries stay complete without having to
	 * also resolve Account -> Employee. [AuthService]'s entry is entityType="Account"/entityId=
	 * <account id>, and is the ONLY entry written when the account has no linked Employee (e.g.
	 * the bootstrap SUPER_ADMIN) — that path never reaches this method at all. Collapsing to a
	 * single entry would either lose the Employee-keyed record for this endpoint or leave the
	 * generic endpoint unaudited for employee-linked accounts; two entries is the simpler,
	 * lower-risk trade-off given audit consumers here are still early (see [AuditService] kdoc:
	 * "Skeleton only").
	 */
	@Transactional
	fun resetPassword(id: UUID, actorAccountId: UUID?, actorRole: RoleType): ResetPasswordResponse {
		findEmployeeOrThrow(id)
		val account = accountRepository.findByEmployeeId(id)
			.orElseThrow { ResourceNotFoundException("error.employee.account-not-found", id) }
		RoleHierarchyGuard.assertCanManage(actorRole, account.role)

		val response = authService.resetPassword(requireNotNull(account.id), actorAccountId, actorRole)

		auditService.record(actorAccountId, "EMPLOYEE_PASSWORD_RESET", "Employee", id)

		return response
	}

	private fun findEmployeeOrThrow(id: UUID): Employee =
		employeeRepository.findById(id).orElseThrow { ResourceNotFoundException("error.employee.not-found", id) }

	private fun buildSpecification(
		regionName: String?,
		status: EmployeeStatus?,
		employeeIds: Set<UUID>?
	): Specification<Employee> =
		Specification { root, _, cb ->
			val predicates = mutableListOf<Predicate>()
			regionName?.let { predicates.add(cb.equal(root.get<String>("regionName"), it)) }
			status?.let { predicates.add(cb.equal(root.get<EmployeeStatus>("status"), it)) }
			employeeIds?.let { predicates.add(root.get<UUID>("id").`in`(it)) }
			cb.and(*predicates.toTypedArray())
		}

	// --- Username auto-generation (Cyrillic/Uzbek-aware transliteration) ---

	private val cyrillicToLatin = mapOf(
		'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
		'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
		'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
		'ф' to "f", 'х' to "x", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "",
		'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
		'ў' to "o", 'қ' to "q", 'ғ' to "g", 'ҳ' to "h"
	)

	private fun transliterate(input: String): String =
		input.lowercase().map { ch -> cyrillicToLatin[ch] ?: ch.toString() }.joinToString("")

	/**
	 * e.g. "Aliyev Vali Bekovich" -> "aliyev.vali.bekovich", de-duplicated against
	 * existing usernames with a numeric suffix. Truncated to [USERNAME_BASE_MAX_LENGTH]
	 * (well under Account.username's 64-char column) so a long full name can't blow
	 * past the column limit and surface as a confusing DataIntegrityViolationException
	 * (which the caller would otherwise see reported as a generic 500/constraint error
	 * instead of a validation problem).
	 */
	private fun generateUsername(fullName: String): String {
		val base = transliterate(fullName)
			.replace(Regex("[^a-z0-9]+"), ".")
			.trim('.')
			.take(USERNAME_BASE_MAX_LENGTH)
			.trim('.')
			.ifBlank { "user" }

		var candidate = base
		var suffix = 1
		while (accountRepository.existsByUsername(candidate)) {
			candidate = "$base$suffix"
			suffix++
		}
		return candidate
	}

	private fun getPhotoUrl(photoKey: String?): String? =
		photoKey?.let { fileStorageService.presignedGetUrl(it) }

	@Transactional
	fun uploadPhoto(id: UUID, file: MultipartFile, actorAccountId: UUID?, actorRole: RoleType): EmployeeDto {
		val account = accountRepository.findByEmployeeId(id).orElse(null)
		account?.let { RoleHierarchyGuard.assertCanManage(actorRole, it.role) }
		return uploadPhotoInternal(id, file, actorAccountId)
	}

	@Transactional
	fun uploadMyPhoto(accountId: UUID, file: MultipartFile): EmployeeDto {
		val account = accountRepository.findById(accountId)
			.orElseThrow { ResourceNotFoundException("error.employee.own-account-not-found") }
		val employeeId = account.employeeId
			?: throw BadRequestException("error.employee.own-employee-not-linked")
		return uploadPhotoInternal(employeeId, file, accountId)
	}

	private fun uploadPhotoInternal(employeeId: UUID, file: MultipartFile, actorAccountId: UUID?): EmployeeDto {
		val employee = findEmployeeOrThrow(employeeId)
		val account = accountRepository.findByEmployeeId(employeeId).orElse(null)

		if (file.isEmpty) throw BadRequestException("error.employee.photo-empty")
		if (file.size > MAX_PHOTO_SIZE_BYTES) {
			throw BadRequestException("error.employee.photo-too-large", MAX_PHOTO_SIZE_BYTES / (1024 * 1024))
		}

		val bytes = file.bytes
		val sniffedType = sniffImageType(bytes)
			?: throw BadRequestException("error.employee.photo-invalid-type")
		if (file.contentType != null && file.contentType !in ALLOWED_MIME_TYPES) {
			throw BadRequestException("error.employee.photo-invalid-type")
		}

		val extension = if (sniffedType == "image/png") "png" else "jpg"
		val objectKey = "employees/$employeeId/photo.$extension"
		fileStorageService.upload(objectKey, bytes, sniffedType)

		employee.photoKey = objectKey
		val saved = employeeRepository.save(employee)

		auditService.record(
			actorAccountId = actorAccountId,
			action = "EMPLOYEE_PHOTO_UPLOADED",
			entityType = "Employee",
			entityId = employeeId,
			metadata = "photoKey=$objectKey"
		)

		return EmployeeDto.from(saved, account, getPhotoUrl(saved.photoKey))
	}

	private fun sniffImageType(bytes: ByteArray): String? = when {
		bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
		bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
			bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
		else -> null
	}

	companion object {
		/** Leaves headroom under the 64-char `accounts.username` column for a numeric dedup suffix. */
		private const val USERNAME_BASE_MAX_LENGTH = 55
		private const val MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024 // 5MB
		private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png")
	}
}

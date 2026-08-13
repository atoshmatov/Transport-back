package uz.safecity.transportobserver.ratings.service

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.ratings.dto.InspectorRatingDto
import uz.safecity.transportobserver.ratings.dto.MyRatingDto
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Simple inspector leaderboard (TZ section 7): active INSPECTOR accounts ranked by their
 * COMPLETED [uz.safecity.transportobserver.inspections.entity.Inspection] count, descending.
 * Deliberately not a weighted/multi-factor score — TZ only calls for "eng ko'p tekshiruv bajargan
 * inspektorlar" (most inspections completed), so a plain count is the correct metric here, not an
 * under-spec'd guess at a formula.
 *
 * [uz.safecity.transportobserver.inspections.entity.Inspection.assignedInspectorId] is an
 * [uz.safecity.transportobserver.auth.entity.Account] id, not an
 * [uz.safecity.transportobserver.employees.entity.Employee] id (see that entity's kdoc) — this
 * service joins back to Employee via [uz.safecity.transportobserver.auth.entity.Account.employeeId]
 * purely to surface a human-readable [InspectorRatingDto.fullName]/`employeeId` for the UI; the
 * ranking/counting itself is always keyed by account id. An active INSPECTOR account missing an
 * employeeId (should not happen in practice — [uz.safecity.transportobserver.employees.service.EmployeeService.create]
 * always sets it when provisioning an account) is defensively skipped from the ranking entirely,
 * since the DTO contract requires a non-null employeeId.
 */
@Service
class RatingService(
	private val accountRepository: AccountRepository,
	private val employeeRepository: EmployeeRepository,
	private val inspectionRepository: InspectionRepository
) {

	fun getTop(): List<InspectorRatingDto> = fullRanking().take(TOP_LIMIT)

	/**
	 * INSPECTOR callers get their own stats always (even 0 completed inspections, even outside
	 * [TOP_LIMIT]) — [MyRatingDto.rank] is only non-null when they'd also show up on [getTop]'s
	 * list, per TZ. Every other role has no personal inspection stats to report (an ADMIN doesn't
	 * perform inspections), so this returns null and the controller turns that into `204 No
	 * Content` — not `404`, since "you have no rating" isn't an error, just an empty result.
	 *
	 * A logged-in INSPECTOR is guaranteed to have an active [uz.safecity.transportobserver.auth.entity.Account]
	 * (a blocked/`isActive = false` account can't authenticate at all — see `AuthService.login`),
	 * so this only needs to defend against the (should-not-happen) missing-employeeId edge case
	 * described in the class kdoc.
	 */
	fun getMyRating(principal: CustomUserDetails): MyRatingDto? {
		if (principal.role != RoleType.INSPECTOR) return null

		val account = accountRepository.findById(principal.accountId).orElse(null) ?: return null
		val employeeId = account.employeeId ?: return null
		val employee = employeeRepository.findById(employeeId).orElse(null) ?: return null

		val completedCount = inspectionRepository
			.countByAssignedInspectorIdAndStatus(principal.accountId, InspectionStatus.COMPLETED)

		val rankInTop = fullRanking().take(TOP_LIMIT).indexOfFirst { it.employeeId == employeeId }
		val rank = if (rankInTop >= 0) rankInTop + 1 else null

		return MyRatingDto(
			employeeId = employeeId,
			fullName = employee.fullName,
			completedInspectionsCount = completedCount.toInt(),
			rank = rank
		)
	}

	/**
	 * Every active INSPECTOR account (with a resolvable Employee), ranked descending by COMPLETED
	 * inspection count — not paginated/limited; [getTop] and [getMyRating] slice/search this as
	 * needed. Two batched queries total (accounts-by-role, completed-counts-grouped-by-inspector),
	 * never one query per inspector.
	 */
	private fun fullRanking(): List<InspectorRatingDto> {
		val activeInspectors = accountRepository.findByRoleAndIsActive(RoleType.INSPECTOR, true)
			.filter { it.employeeId != null }
		if (activeInspectors.isEmpty()) return emptyList()

		val countsByAccountId = inspectionRepository
			.countCompletedGroupByInspector(InspectionStatus.COMPLETED)
			.associate { it.accountId to it.cnt }

		val employeesById = employeeRepository
			.findAllById(activeInspectors.map { requireNotNull(it.employeeId) })
			.associateBy { requireNotNull(it.id) }

		return activeInspectors
			.mapNotNull { account ->
				val employeeId = requireNotNull(account.employeeId)
				val employee = employeesById[employeeId] ?: return@mapNotNull null
				val count = countsByAccountId[requireNotNull(account.id)] ?: 0L
				InspectorRatingDto(
					employeeId = employeeId,
					fullName = employee.fullName,
					completedInspectionsCount = count.toInt(),
					rank = 0 // placeholder — overwritten below once the final sort order is known
				)
			}
			.sortedByDescending { it.completedInspectionsCount }
			.mapIndexed { index, dto -> dto.copy(rank = index + 1) }
	}

	companion object {
		private const val TOP_LIMIT = 20
	}
}

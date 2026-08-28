package uz.safecity.transportobserver.inspector.service

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.employees.dto.EmployeePositionHistoryDto
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.employees.service.EmployeePositionHistoryService
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.inspector.dto.ActivityTone
import uz.safecity.transportobserver.inspector.dto.ProfileActivityDto
import uz.safecity.transportobserver.inspector.dto.ProfileDetailDto
import uz.safecity.transportobserver.inspector.dto.VehicleSummaryDto
import uz.safecity.transportobserver.ratings.service.RatingService
import uz.safecity.transportobserver.shifts.repository.WorkShiftRepository
import uz.safecity.transportobserver.vehicles.repository.VehicleRepository
import org.springframework.stereotype.Service

/**
 * Backs `GET /api/v1/inspector/me/profile-detail` — the mobile "Xodim kartasi" (`profileDetail`,
 * opened from the Profile tab, `backTo: 'profile'`) full-profile screen — and
 * `GET /api/v1/inspector/me/position-history` (see [getMyPositionHistory]), that screen's
 * lavozim/hudud o'zgarish jurnali.
 *
 * The design (`TO-Screen.dc.html` `profileDetail`, ~line 813) shows a LOT more than the original
 * columns covered: JSHSHIR, birth date, home address, email, service-certificate number/expiry,
 * driving license category, attestation dates, assigned tablet/body camera.
 * [uz.safecity.transportobserver.employees.entity.Employee] now has real columns for all of these
 * (see that entity's kdoc) — there is still no admin-panel form to enter them yet (a later, separate
 * task), so most existing rows will simply have `null` there until that form lands. `null` here
 * always means "not entered yet", never a fabricated/guessed value. This DTO covers ONLY what a
 * real column or a real, already-existing service can answer:
 *  - [uz.safecity.transportobserver.employees.entity.Employee]'s columns (name, position,
 *    department, region, phone, hire date, photo key, and the HR fields above),
 *  - the leaderboard rank/completed-count metric, reusing [RatingService.getMyRating] rather than
 *    re-deriving it (this codebase already fixed a fabricated "4.8 rating" bug once — see that
 *    method's kdoc — this must not reintroduce a second fabricated number next to it),
 *  - the inspector's currently-assigned service vehicle, via the reverse
 *    [uz.safecity.transportobserver.vehicles.entity.Vehicle.assignedEmployeeId] lookup,
 *  - a merged recent-activity timeline built from 3 real event sources (own Incidents, own
 *    COMPLETED Inspections, own WorkShift check-ins) — see [getRecentActivity].
 *
 * Scoped to the caller's own [uz.safecity.transportobserver.employees.entity.Employee] row only —
 * resolved via `principal.accountId` -> [uz.safecity.transportobserver.auth.entity.Account.employeeId]
 * -> [uz.safecity.transportobserver.employees.entity.Employee.id], same chain
 * [RatingService.getMyRating] uses. There is no path here to look up another inspector's profile.
 */
@Service
class ProfileDetailService(
	private val accountRepository: AccountRepository,
	private val employeeRepository: EmployeeRepository,
	private val vehicleRepository: VehicleRepository,
	private val ratingService: RatingService,
	private val incidentRepository: IncidentRepository,
	private val inspectionRepository: InspectionRepository,
	private val workShiftRepository: WorkShiftRepository,
	private val employeePositionHistoryService: EmployeePositionHistoryService
) {

	fun getMyProfileDetail(principal: CustomUserDetails): ProfileDetailDto {
		assertInspector(principal.role)
		val accountId = principal.accountId
		val employeeId = resolveMyEmployeeId(accountId)
		val employee = employeeRepository.findById(employeeId).orElse(null)
			?: throw ResourceNotFoundException("error.employee.not-found", employeeId)

		// Reuse the existing leaderboard metric rather than re-deriving completedCount/rank here —
		// see class kdoc re: not reintroducing a second fabricated/duplicated number.
		val myRating = ratingService.getMyRating(principal)

		val assignedVehicle = vehicleRepository.findFirstByAssignedEmployeeIdAndIsActiveTrue(employeeId)
			?.let { VehicleSummaryDto.from(it) }

		return ProfileDetailDto.from(
			employee = employee,
			completedInspectionsCount = myRating?.completedInspectionsCount ?: 0,
			rank = myRating?.rank,
			assignedVehicle = assignedVehicle,
			recentActivity = getRecentActivity(accountId)
		)
	}

	/**
	 * Backs `GET /api/v1/inspector/me/position-history` — the mobile profileDetail screen's
	 * lavozim/hudud o'zgarish jurnali, scoped to the caller's OWN
	 * [uz.safecity.transportobserver.employees.entity.Employee] row via the same
	 * `principal.accountId` -> `Account.employeeId` chain as [getMyProfileDetail] (there is no path
	 * here to look up another inspector's history — unlike the admin-only
	 * `GET /api/v1/admin/employees/{id}/position-history`, which takes an arbitrary path `id`).
	 * Delegates the actual query to [EmployeePositionHistoryService.list], the same method backing
	 * that admin endpoint (see [uz.safecity.transportobserver.employees.controller.AdminEmployeeController.getPositionHistory]).
	 */
	fun getMyPositionHistory(principal: CustomUserDetails): List<EmployeePositionHistoryDto> {
		assertInspector(principal.role)
		val employeeId = resolveMyEmployeeId(principal.accountId)
		return employeePositionHistoryService.list(employeeId)
	}

	/**
	 * A logged-in INSPECTOR is guaranteed an active Account (blocked accounts can't authenticate,
	 * see AuthService.login) with an employeeId (EmployeeService.create always sets it when
	 * provisioning an INSPECTOR account) — same guarantee RatingService.getMyRating relies on.
	 */
	private fun resolveMyEmployeeId(accountId: java.util.UUID): java.util.UUID {
		val account = accountRepository.findById(accountId).orElse(null)
			?: throw ResourceNotFoundException("error.employee.not-found", accountId)
		return account.employeeId
			?: throw ResourceNotFoundException("error.employee.not-found", accountId)
	}

	/**
	 * Merges 3 independently-ordered event sources into one "so'nggi faoliyat" timeline, newest
	 * first, capped at [RECENT_ACTIVITY_LIMIT] — deliberately NOT a single [Incident]-only feed
	 * like [InspectorPanelService.getRecentActivity] (that backs a different screen). Each source
	 * is queried with its own small `Top N` limit (never the full history) since only the newest
	 * handful across all 3 can ever survive the final merge-and-cap anyway.
	 */
	private fun getRecentActivity(accountId: java.util.UUID): List<ProfileActivityDto> {
		val incidentEvents = incidentRepository.findTop10ByAssignedInspectorIdOrderByCreatedAtDesc(accountId)
			.map { incident ->
				ProfileActivityDto(
					label = "Hodisa qayd etdi",
					occurredAt = incident.occurredAt ?: requireNotNull(incident.createdAt),
					tone = ActivityTone.ACCENT
				)
			}

		val inspectionEvents = inspectionRepository
			.findTop5ByAssignedInspectorIdAndStatusOrderByPerformedAtDesc(accountId, InspectionStatus.COMPLETED)
			.map { inspection ->
				ProfileActivityDto(
					label = "Tekshiruvni yakunladi",
					occurredAt = requireNotNull(inspection.performedAt),
					tone = ActivityTone.ACCENT
				)
			}

		val shiftEvents = workShiftRepository.findTop5ByInspectorIdOrderByStartedAtDesc(accountId)
			.map { shift ->
				ProfileActivityDto(
					label = "Navbatchilikni boshladi",
					occurredAt = shift.startedAt,
					tone = ActivityTone.INFO
				)
			}

		return (incidentEvents + inspectionEvents + shiftEvents)
			.sortedByDescending(ProfileActivityDto::occurredAt)
			.take(RECENT_ACTIVITY_LIMIT)
	}

	/**
	 * Defense-in-depth mirror of the controller's `@PreAuthorize` — same pattern as
	 * [InspectorPanelService.assertInspector].
	 */
	private fun assertInspector(role: RoleType) {
		if (role != RoleType.INSPECTOR) {
			throw ForbiddenException("error.inspector.panel-forbidden")
		}
	}

	companion object {
		private const val RECENT_ACTIVITY_LIMIT = 10
	}
}

package uz.safecity.transportobserver.inspector.dto

import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.vehicles.entity.Vehicle
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Visual weight hint for a single [ProfileActivityDto] row on the mobile "Xodim kartasi"
 * (`profileDetail`) timeline — mirrors the design's per-row accent color
 * (`TO-Screen.dc.html` `profileDetail.timeline`, e.g. `AC`/`BL`) without hardcoding a color value
 * server-side; the mobile client maps this to its own palette.
 */
enum class ActivityTone { ACCENT, INFO, NEUTRAL }

/**
 * Response for `GET /api/v1/inspector/me/profile-detail` — the mobile "Xodim kartasi"
 * (`profileDetail`, `backTo: 'profile'`) full-profile screen. See
 * [uz.safecity.transportobserver.inspector.service.ProfileDetailService] kdoc.
 *
 * The design (`TO-Screen.dc.html` `profileDetail`, ~line 813) originally showed several fields
 * ([personalId] through [assignedBadgeCameraId]) that had no backing column at all —
 * [uz.safecity.transportobserver.employees.entity.Employee] has since gained them (see that
 * entity's kdoc). They are still all nullable here: most existing employee rows have none of it
 * entered yet, and there is (as of this addition) still no admin-panel form to enter it — that UI
 * is a separate, later task. `null` means "not entered yet", never fabricated. Every other field
 * below is backed by a real column or a real, already-existing service
 * ([uz.safecity.transportobserver.ratings.service.RatingService]) — nothing here is invented.
 */
data class ProfileDetailDto(
	val employeeId: UUID,
	val fullName: String,
	val position: String?,
	val department: String?,
	val regionName: String?,
	val phoneNumber: String?,
	val hiredAt: LocalDate?,
	val photoKey: String?,
	/** Same metric as [uz.safecity.transportobserver.ratings.dto.MyRatingDto.completedInspectionsCount] — see that DTO's kdoc. */
	val completedInspectionsCount: Int,
	/** Same as [uz.safecity.transportobserver.ratings.dto.MyRatingDto.rank] — null when outside the top-20 leaderboard. */
	val rank: Int?,
	val assignedVehicle: VehicleSummaryDto?,
	val recentActivity: List<ProfileActivityDto>,
	// --- HR "full profile" fields — see class kdoc. All nullable: `null` = not entered yet. ---
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
		fun from(
			employee: Employee,
			completedInspectionsCount: Int,
			rank: Int?,
			assignedVehicle: VehicleSummaryDto?,
			recentActivity: List<ProfileActivityDto>
		) = ProfileDetailDto(
			employeeId = requireNotNull(employee.id),
			fullName = employee.fullName,
			position = employee.position,
			department = employee.department,
			regionName = employee.regionName,
			phoneNumber = employee.phoneNumber,
			hiredAt = employee.hiredAt,
			photoKey = employee.photoKey,
			completedInspectionsCount = completedInspectionsCount,
			rank = rank,
			assignedVehicle = assignedVehicle,
			recentActivity = recentActivity,
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

/**
 * "Xizmat avtomobili" line on the profile card — deliberately narrow (no `ownerType`/`regionName`/
 * `isActive` admin bookkeeping fields), same "expose only what this screen needs" reasoning as
 * [uz.safecity.transportobserver.vehicles.dto.VehiclePickerDto].
 */
data class VehicleSummaryDto(
	val id: UUID,
	val plateNumber: String,
	val model: String?
) {
	companion object {
		fun from(vehicle: Vehicle) = VehicleSummaryDto(
			id = requireNotNull(vehicle.id),
			plateNumber = vehicle.plateNumber,
			model = vehicle.model
		)
	}
}

/**
 * `profileDetail`'s "SO'NGGI FAOLIYAT" timeline row. See
 * [uz.safecity.transportobserver.inspector.service.ProfileDetailService.getRecentActivity] for the
 * 3 real event sources this is merged from (own [uz.safecity.transportobserver.incidents.entity.Incident]
 * reports, own COMPLETED [uz.safecity.transportobserver.inspections.entity.Inspection]s, own
 * [uz.safecity.transportobserver.shifts.entity.WorkShift] check-ins) — deliberately NOT the same
 * DTO/data source as [RecentActivityDto] (that one is Incident-only, backs a different screen's
 * "so'nggi ishlar" widget). [occurredAt] is a real `Instant`, not a pre-formatted string — same
 * "let the client format it" convention as every other timestamp field in this codebase.
 */
data class ProfileActivityDto(
	val label: String,
	val occurredAt: Instant,
	val tone: ActivityTone
)

package uz.safecity.transportobserver.map.service

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.common.util.GeoUtils
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.map.dto.EmployeeLocationDto
import uz.safecity.transportobserver.map.dto.UpdateInspectorLocationRequest
import uz.safecity.transportobserver.map.entity.InspectorLocation
import uz.safecity.transportobserver.map.repository.InspectorLocationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Backs the inspector live-location MVP (TZ map/dashboard "xaritadagi xodimlar" clusters +
 * category legend): `POST /api/v1/inspector/me/location` (mobile heartbeat, write side — see
 * [uz.safecity.transportobserver.inspector.controller.InspectorPanelController]) and
 * `GET /api/v1/map/employees` (Admin/Operator map view, read side — see
 * [uz.safecity.transportobserver.map.controller.MapController]).
 *
 * Latest-only, no history: [upsertMyLocation] always overwrites the caller's own single
 * [InspectorLocation] row instead of inserting a new one — see that entity's kdoc.
 */
@Service
class InspectorLocationService(
	private val inspectorLocationRepository: InspectorLocationRepository,
	private val accountRepository: AccountRepository,
	private val employeeRepository: EmployeeRepository
) {

	/**
	 * Scoped to the caller's own `accountId` (from the JWT principal, never a client-supplied id)
	 * — an inspector can only ever overwrite their own location, matching every other `/me/...`
	 * endpoint on [uz.safecity.transportobserver.inspector.controller.InspectorPanelController].
	 *
	 * Delegates the actual write to [InspectorLocationRepository.upsertLocation] (native
	 * `INSERT ... ON CONFLICT`) rather than a find-then-save — see that method's kdoc for why a
	 * plain `findByInspectorId` + `save` is not safe here (two concurrent calls for the same
	 * inspector, e.g. a mobile retry, could both miss and both insert, and the loser would blow up
	 * on the unique constraint).
	 */
	@Transactional
	fun upsertMyLocation(principal: CustomUserDetails, request: UpdateInspectorLocationRequest) {
		assertInspector(principal.role)
		val latitude = requireNotNull(request.latitude) { "latitude majburiy" }
		val longitude = requireNotNull(request.longitude) { "longitude majburiy" }
		GeoUtils.toPoint(latitude, longitude) // range validation only — the native upsert builds the point itself

		inspectorLocationRepository.upsertLocation(
			id = UUID.randomUUID(),
			inspectorId = principal.accountId,
			latitude = latitude,
			longitude = longitude,
			now = Instant.now()
		)
	}

	/**
	 * Every inspector's latest known position, enriched with a human-readable name via the same
	 * Account -> Employee batched-lookup pattern as
	 * [uz.safecity.transportobserver.ratings.service.RatingService.fullRanking] (two extra
	 * queries total, never one per row). A row whose account/employee isn't resolvable (should
	 * not happen in practice — see that kdoc) still renders with a placeholder name rather than
	 * being dropped, since a location on the map shouldn't silently disappear over a name lookup
	 * miss.
	 */
	fun listEmployeeLocations(): List<EmployeeLocationDto> {
		val locations = inspectorLocationRepository.findAll()
		if (locations.isEmpty()) return emptyList()

		val accountsById = accountRepository.findAllById(locations.map { it.inspectorId })
			.associateBy { requireNotNull(it.id) }
		val employeeIds = accountsById.values.mapNotNull { it.employeeId }
		val employeesById = employeeRepository.findAllById(employeeIds).associateBy { requireNotNull(it.id) }

		val now = Instant.now()
		return locations.map { location ->
			val employeeId = accountsById[location.inspectorId]?.employeeId
			val employee = employeeId?.let { employeesById[it] }
			val online = Duration.between(requireNotNull(location.updatedAt), now) <= ONLINE_WINDOW
			EmployeeLocationDto.from(location, employee, online)
		}
	}

	/**
	 * Defense-in-depth mirror of the controller's `@PreAuthorize` — same pattern as
	 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService.assertInspector].
	 */
	private fun assertInspector(role: RoleType) {
		if (role != RoleType.INSPECTOR) {
			throw ForbiddenException("Faqat inspektorlar o'z joylashuvini yubora oladi")
		}
	}

	companion object {
		/** Matches the task spec's "oxirgi N daqiqa" MVP window (N = 5). */
		private val ONLINE_WINDOW = Duration.ofMinutes(5)
	}
}

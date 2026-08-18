package uz.safecity.transportobserver.map.service

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.common.util.GeoUtils
import uz.safecity.transportobserver.common.util.PresenceUtils
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.map.dto.EmployeeLocationDto
import uz.safecity.transportobserver.map.dto.UpdateInspectorLocationRequest
import uz.safecity.transportobserver.map.entity.InspectorLocation
import uz.safecity.transportobserver.map.repository.InspectorLocationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
	 * Every inspector that's either sent a GPS heartbeat before OR is currently session-active
	 * (logged in from web or mobile, per [uz.safecity.transportobserver.auth.security.PresenceTracker]),
	 * enriched with a human-readable name via the same Account -> Employee batched-lookup
	 * pattern as [uz.safecity.transportobserver.ratings.service.RatingService.fullRanking].
	 *
	 * [online] combines BOTH presence signals — GPS heartbeat freshness
	 * ([InspectorLocation.updatedAt]) and session-activity freshness ([Account.lastActiveAt]) —
	 * so an inspector who is logged into the web panel right now shows as online even before/
	 * without ever sending a location (see [EmployeeLocationDto] kdoc: `latitude`/`longitude`
	 * stay `null` in that case, there's simply no position to show yet). Conversely, an inspector
	 * who sent a heartbeat 2 minutes ago but whose session token then expired still correctly
	 * shows online from the location signal alone.
	 *
	 * An inspector who has neither a location row nor a recent [Account.lastActiveAt] is left out
	 * entirely (same as the old behavior for "no location ever sent") — otherwise every inspector
	 * ever provisioned, active or not, would permanently clutter this map.
	 *
	 * A row whose account/employee isn't resolvable (should not happen in practice — see
	 * [uz.safecity.transportobserver.ratings.service.RatingService.fullRanking] kdoc) still
	 * renders with a placeholder name rather than being dropped, since a location on the map
	 * shouldn't silently disappear over a name lookup miss.
	 */
	fun listEmployeeLocations(): List<EmployeeLocationDto> {
		val locations = inspectorLocationRepository.findAll()
		val locationsByInspectorId = locations.associateBy { it.inspectorId }
		val now = Instant.now()

		// Accounts behind an existing location row (even if stale/offline) + INSPECTOR accounts
		// that are currently session-active but have no (or a stale) location row — the union is
		// exactly "every inspector worth showing on the map right now".
		val accountsWithLocation = accountRepository.findAllById(locationsByInspectorId.keys)
		val sessionActiveInspectors = accountRepository.findByRoleAndLastActiveAtGreaterThanEqual(
			RoleType.INSPECTOR,
			now.minus(PresenceUtils.ONLINE_WINDOW)
		)

		val accountsById = (accountsWithLocation + sessionActiveInspectors)
			.associateBy { requireNotNull(it.id) }
		if (accountsById.isEmpty()) return emptyList()

		val employeeIds = accountsById.values.mapNotNull { it.employeeId }
		val employeesById = employeeRepository.findAllById(employeeIds).associateBy { requireNotNull(it.id) }

		return accountsById.map { (inspectorId, account) ->
			val location = locationsByInspectorId[inspectorId]
			val employee = account.employeeId?.let { employeesById[it] }
			val online = PresenceUtils.isRecent(location?.updatedAt, now) ||
				PresenceUtils.isRecent(account.lastActiveAt, now)
			val lastSeenAt = PresenceUtils.latestOf(location?.updatedAt, account.lastActiveAt)
			EmployeeLocationDto.from(inspectorId, location, employee, online, lastSeenAt)
		}
	}

	/**
	 * Defense-in-depth mirror of the controller's `@PreAuthorize` — same pattern as
	 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService.assertInspector].
	 */
	private fun assertInspector(role: RoleType) {
		if (role != RoleType.INSPECTOR) {
			throw ForbiddenException("error.inspector.location-forbidden")
		}
	}
}

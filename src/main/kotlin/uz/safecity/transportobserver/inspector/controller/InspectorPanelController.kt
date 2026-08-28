package uz.safecity.transportobserver.inspector.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.employees.dto.EmployeePositionHistoryDto
import uz.safecity.transportobserver.incidents.dto.CreateSosRequest
import uz.safecity.transportobserver.incidents.dto.IncidentDto
import uz.safecity.transportobserver.incidents.service.IncidentService
import uz.safecity.transportobserver.inspector.dto.ActionTypeCountDto
import uz.safecity.transportobserver.inspector.dto.DashboardSummaryDto
import uz.safecity.transportobserver.inspector.dto.IncidentTypeCountDto
import uz.safecity.transportobserver.inspector.dto.InspectorCurrentLocationDto
import uz.safecity.transportobserver.inspector.dto.InspectorStatsDto
import uz.safecity.transportobserver.inspector.dto.ProfileDetailDto
import uz.safecity.transportobserver.inspector.dto.RecentActivityDto
import uz.safecity.transportobserver.inspector.dto.VehicleDetailDto
import uz.safecity.transportobserver.inspector.service.InspectorPanelService
import uz.safecity.transportobserver.inspector.service.ProfileDetailService
import uz.safecity.transportobserver.map.dto.UpdateInspectorLocationRequest
import uz.safecity.transportobserver.map.service.InspectorLocationService
import uz.safecity.transportobserver.shifts.dto.StartShiftRequest
import uz.safecity.transportobserver.shifts.dto.WorkShiftDto
import uz.safecity.transportobserver.shifts.service.WorkShiftService
import uz.safecity.transportobserver.vehicles.dto.VehiclePickerDto
import uz.safecity.transportobserver.vehicles.service.VehicleService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Inspector web-panel dashboard + map screens. Every endpoint here is
 * INSPECTOR-only and scoped to the caller's own assigned incidents — see
 * [InspectorPanelService] kdoc for the scoping pattern. `/me/location` is
 * scoped the same way but backed by [InspectorLocationService] (`map` module)
 * instead — see that service's kdoc. `/me/shift/...` is backed by
 * [WorkShiftService] (`shifts` module) — see [uz.safecity.transportobserver.shifts.entity.WorkShift]
 * kdoc for how "on duty" (explicit shift check-in) differs from the `online` presence signal.
 * `/vehicles` is backed by [VehicleService] (`vehicles` module) instead — see
 * [listVehiclesForPicker] kdoc for why this is a separate, narrower endpoint from
 * [uz.safecity.transportobserver.vehicles.controller.VehicleController.list]. `/vehicles/{id}` is
 * NOT scoped to the caller's own assigned incidents (unlike every other endpoint here) — see
 * [getVehicleDetail] kdoc for why a vehicle's own violation history is not "my" data.
 */
@RestController
@RequestMapping("/api/v1/inspector")
class InspectorPanelController(
	private val inspectorPanelService: InspectorPanelService,
	private val inspectorLocationService: InspectorLocationService,
	private val workShiftService: WorkShiftService,
	private val incidentService: IncidentService,
	private val vehicleService: VehicleService,
	private val profileDetailService: ProfileDetailService
) {

	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/dashboard/summary")
	fun getDashboardSummary(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<DashboardSummaryDto>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getDashboardSummary(principal)))

	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/map/current-location")
	fun getCurrentLocation(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<InspectorCurrentLocationDto>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getCurrentLocation(principal)))

	/** Mobile Profile screen "5 asosiy metrik" block — see [InspectorStatsDto] kdoc. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/stats")
	fun getMyStats(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<InspectorStatsDto>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getMyStats(principal)))

	/** Mobile Profile screen "hodisa turi bo'yicha" donut chart — see [IncidentTypeCountDto] kdoc. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/incident-type-breakdown")
	fun getMyIncidentTypeBreakdown(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<List<IncidentTypeCountDto>>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getIncidentTypeBreakdown(principal)))

	/** Mobile Profile screen "harakat turi bo'yicha" donut chart — see [ActionTypeCountDto] kdoc. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/action-type-breakdown")
	fun getMyActionTypeBreakdown(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<List<ActionTypeCountDto>>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getActionTypeBreakdown(principal)))

	/** Mobile Profile screen "so'nggi ishlar" list — see [RecentActivityDto] kdoc. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/recent-activity")
	fun getMyRecentActivity(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<List<RecentActivityDto>>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getRecentActivity(principal)))

	/**
	 * Periodic foreground-location heartbeat from the mobile app (per task scope: every 1-2 min).
	 * Upserts the caller's own single latest-position row — see [InspectorLocationService] kdoc.
	 * `204 No Content` on success, same "no meaningful body" convention as
	 * [uz.safecity.transportobserver.auth.controller.AuthController]'s ack endpoints.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@PostMapping("/me/location")
	fun updateMyLocation(
		@AuthenticationPrincipal principal: CustomUserDetails,
		@Valid @RequestBody request: UpdateInspectorLocationRequest
	): ResponseEntity<Void> {
		inspectorLocationService.upsertMyLocation(principal, request)
		return ResponseEntity.noContent().build()
	}

	/**
	 * "Ishga chiqdim" — opens a new shift. `409` (via [uz.safecity.transportobserver.common.exception.ConflictException])
	 * if one is already open. The body is optional (mirrors [createSos]'s "optional body"
	 * convention) — an older mobile client that sends no body, or an empty one, simply starts a
	 * shift with no checkpoint check-in, see [StartShiftRequest]/[uz.safecity.transportobserver.shifts.entity.WorkShift.checkpointId] kdoc.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@PostMapping("/me/shift/start")
	fun startShift(
		@AuthenticationPrincipal principal: CustomUserDetails,
		@RequestBody(required = false) request: StartShiftRequest?
	): ResponseEntity<ApiResponse<WorkShiftDto>> =
		ResponseEntity.status(HttpStatus.CREATED).body(
			ApiResponse.ok(workShiftService.startShift(principal, request?.checkpointId))
		)

	/** "Ishni tugatdim" — closes the caller's open shift. `409` if there is none. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@PostMapping("/me/shift/end")
	fun endShift(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<WorkShiftDto>> =
		ResponseEntity.ok(ApiResponse.ok(workShiftService.endShift(principal)))

	/** `204 No Content` when there's no open shift — same "no meaningful body" convention as [updateMyLocation]. */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/shift/current")
	fun getCurrentShift(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<WorkShiftDto>> {
		val current = workShiftService.getCurrent(principal) ?: return ResponseEntity.noContent().build()
		return ResponseEntity.ok(ApiResponse.ok(current))
	}

	/**
	 * Panic button. `latitude`/`longitude` are optional (best-effort, may be absent — see
	 * [CreateSosRequest] kdoc). See [IncidentService.createSos] for the full effect: creates an
	 * [uz.safecity.transportobserver.incidents.entity.Incident] with `isSos=true`, notifies every
	 * active SUPER_ADMIN/ADMIN/OPERATOR, and broadcasts over STOMP to `/topic/sos` in real time.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@PostMapping("/me/sos")
	fun createSos(
		@AuthenticationPrincipal principal: CustomUserDetails,
		@RequestBody(required = false) request: CreateSosRequest?
	): ResponseEntity<ApiResponse<IncidentDto>> =
		ResponseEntity.status(HttpStatus.CREATED).body(
			ApiResponse.ok(incidentService.createSos(request ?: CreateSosRequest(), principal))
		)

	/**
	 * Take-back window for a misclick — see [IncidentService.cancelSos] for why this is only
	 * honored within 5 seconds of the SOS being created, and only on the caller's own SOS.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@PostMapping("/me/sos/{id}/cancel")
	fun cancelSos(
		@PathVariable id: UUID,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<IncidentDto>> =
		ResponseEntity.ok(ApiResponse.ok(incidentService.cancelSos(id, principal)))

	/**
	 * Lightweight transport picker for the mobile "hodisa qayd etish" (report incident) flow —
	 * an INSPECTOR may optionally attach a vehicle to a new incident report and needs *some* way
	 * to look one up by plate number or model. Deliberately NOT the same endpoint as
	 * [uz.safecity.transportobserver.vehicles.controller.VehicleController.list] — see that
	 * controller's kdoc for why the full roster (with `ownerType`/`assignedEmployeeId`/
	 * `regionName`/`isActive` and no page-size cap) is an Admin/Operator-only capability per the
	 * TZ 5.6 permissions matrix. This endpoint instead:
	 *  - is INSPECTOR-only (not admin-role-gated),
	 *  - returns only [VehiclePickerDto]'s 4 fields (no admin bookkeeping data),
	 *  - only ever returns active vehicles (see [VehicleService.listForInspectorPicker]),
	 *  - defaults to a small page size (30) since this backs a search-as-you-type picker, not a
	 *    full-roster admin table.
	 * Not scoped to "my" vehicles (unlike this controller's other `/me/...` endpoints) — a vehicle
	 * has no owning inspector concept (see [uz.safecity.transportobserver.vehicles.entity.Vehicle]
	 * kdoc re: `assignedEmployeeId` being HR/fleet bookkeeping, not an auth principal), so every
	 * active vehicle is a valid pick for any inspector.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/vehicles")
	fun listVehiclesForPicker(
		@RequestParam(required = false) query: String?,
		@PageableDefault(size = 30) pageable: Pageable
	): ResponseEntity<ApiResponse<PageResponse<VehiclePickerDto>>> =
		ResponseEntity.ok(ApiResponse.ok(vehicleService.listForInspectorPicker(query, pageable)))

	/**
	 * Mobile "Transport vositasi" (vehicleDetail) screen — opened from the vehicle line on the
	 * "Hodisa kartasi" (incidentDetail) screen, or from a recent-incident row on Home. See
	 * [uz.safecity.transportobserver.inspector.dto.VehicleDetailDto] kdoc for exactly what this
	 * deliberately does NOT surface (VIN/owner-org/STIR/route-permit/inspection-date master data —
	 * admin-panel-only fields not yet on [uz.safecity.transportobserver.vehicles.entity.Vehicle];
	 * adding them is out of scope here, since it would require a new web-frontend form).
	 *
	 * INSPECTOR-only, same as every other endpoint on this controller — NOT admin-role-gated like
	 * [uz.safecity.transportobserver.vehicles.controller.VehicleController.getById]. This is a
	 * separate, narrower, INSPECTOR-facing detail view rather than a role carve-out on that admin
	 * endpoint, same split rationale as [listVehiclesForPicker] above (see that method's kdoc).
	 * `404` when [id] doesn't resolve to any [uz.safecity.transportobserver.vehicles.entity.Vehicle] —
	 * see [InspectorPanelService.getVehicleDetail] kdoc.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/vehicles/{id}")
	fun getVehicleDetail(
		@PathVariable id: UUID,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<VehicleDetailDto>> =
		ResponseEntity.ok(ApiResponse.ok(inspectorPanelService.getVehicleDetail(principal, id)))

	/**
	 * Mobile "Xodim kartasi" (`profileDetail`) screen — the inspector's OWN full profile card,
	 * opened from the Profile tab (`backTo: 'profile'`). Backed by [ProfileDetailService] (a
	 * separate service, not [InspectorPanelService]) — see that class's kdoc for exactly which
	 * design fields (JSHSHIR, birth date, home address, email, service certificate, driving
	 * category, attestation dates, assigned tablet/camera) this deliberately leaves out and why:
	 * none of that HR-master data exists anywhere in this codebase yet, and adding it would require
	 * a new web-frontend admin form, which is out of scope here.
	 *
	 * Same INSPECTOR-only, `principal.accountId`-scoped pattern as every other `/me/...` endpoint on
	 * this controller — there is no path here to view another inspector's profile.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/profile-detail")
	fun getMyProfileDetail(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<ProfileDetailDto>> =
		ResponseEntity.ok(ApiResponse.ok(profileDetailService.getMyProfileDetail(principal)))

	/**
	 * Mobile "Xodim kartasi" (`profileDetail`) screen's lavozim/hudud o'zgarish jurnali — the
	 * caller's OWN history only, resolved via `principal.accountId`, same INSPECTOR-only
	 * `/me/...`-scoped pattern as every other endpoint on this controller. See
	 * [ProfileDetailService.getMyPositionHistory] kdoc: this is deliberately a separate, narrower
	 * endpoint from the admin-only
	 * [uz.safecity.transportobserver.employees.controller.AdminEmployeeController.getPositionHistory]
	 * (`GET /api/v1/admin/employees/{id}/position-history`) rather than a role carve-out on it — same
	 * split rationale as [getMyProfileDetail] vs. the admin employee-detail endpoints.
	 */
	@PreAuthorize("hasAuthority('ROLE_INSPECTOR')")
	@GetMapping("/me/position-history")
	fun getMyPositionHistory(
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<List<EmployeePositionHistoryDto>>> =
		ResponseEntity.ok(ApiResponse.ok(profileDetailService.getMyPositionHistory(principal)))
}

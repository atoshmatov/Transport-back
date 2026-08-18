package uz.safecity.transportobserver.map.dto

import uz.safecity.transportobserver.employees.entity.Employee
import uz.safecity.transportobserver.map.entity.InspectorLocation
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/**
 * `POST /api/v1/inspector/me/location` request body — the periodic (every 1-2 min, per task
 * scope) foreground-location heartbeat the mobile app is expected to send. Range validation
 * happens in [uz.safecity.transportobserver.common.util.GeoUtils], same as every other
 * lat/lng-accepting endpoint (Checkpoint/Incident/Evidence).
 */
data class UpdateInspectorLocationRequest(
	@field:NotNull(message = "latitude majburiy")
	val latitude: Double?,

	@field:NotNull(message = "longitude majburiy")
	val longitude: Double?
)

/**
 * `GET /api/v1/map/employees` row — Admin/Operator web-panel map (mockup: clustered inspector
 * pins, e.g. "Toshkent vil.: 28", plus a category legend: "Avtovokzallardagi xodimlar" /
 * "Temiryo'l vokzallaridagi xodimlar" / "Magistral yo'llardagi xodimlar" / "Aeroportlardagi
 * xodimlar"). ADMIN/OPERATOR/SUPER_ADMIN only — see [uz.safecity.transportobserver.map.controller.MapController].
 *
 * [online] is computed at read time (not persisted) from TWO signals — see
 * [uz.safecity.transportobserver.map.service.InspectorLocationService.listEmployeeLocations]
 * kdoc: the GPS heartbeat ([InspectorLocation.updatedAt]) OR any-platform session activity
 * ([uz.safecity.transportobserver.auth.entity.Account.lastActiveAt]). Because of the latter, an
 * inspector can be `online = true` having only ever logged into the web panel and never sent a
 * GPS heartbeat — in that case [latitude]/[longitude] are `null` ("online, position unknown").
 * Frontend must treat a `null` lat/lng as "show as online in a list/badge, but don't attempt to
 * place a pin" rather than defaulting to (0,0) or omitting the row.
 *
 * [lastSeenAt] is the more recent of the two signals (`null` only in the never-should-happen case
 * where neither is present).
 *
 * [category]: the mockup's 4 buckets are exactly
 * [uz.safecity.transportobserver.checkpoints.entity.Checkpoint.type] values, per the existing
 * `GET /api/v1/reports/checkpoints-distribution` legend
 * ([uz.safecity.transportobserver.reports.dto.CheckpointTypeDistributionItemDto]). But — same gap
 * documented in [uz.safecity.transportobserver.inspector.service.InspectorPanelService] kdoc —
 * there is still no Inspector-Checkpoint assignment mechanism anywhere in this codebase (no
 * column/table linking an inspector's account to "their" checkpoint), so there is no honest way
 * to derive which checkpoint/category this inspector belongs to yet. Deliberately `null` here
 * rather than inventing a "nearest checkpoint" heuristic (same reasoning as
 * `InspectorCurrentLocationDto.checkpointName`). Frontend should bucket `null` as an
 * "unclassified/Boshqa" group until a real assignment mechanism exists (TODO, next task).
 */
data class EmployeeLocationDto(
	val inspectorId: UUID,
	val name: String,
	val latitude: Double?,
	val longitude: Double?,
	val online: Boolean,
	val lastSeenAt: Instant?,
	val category: String?
) {
	companion object {
		fun from(
			inspectorId: UUID,
			location: InspectorLocation?,
			employee: Employee?,
			online: Boolean,
			lastSeenAt: Instant?
		) = EmployeeLocationDto(
			inspectorId = inspectorId,
			name = employee?.fullName ?: "Noma'lum inspektor",
			latitude = location?.location?.y,
			longitude = location?.location?.x,
			online = online,
			lastSeenAt = lastSeenAt,
			// TODO (next task): no Inspector-Checkpoint assignment mechanism yet — see kdoc above.
			category = null
		)
	}
}

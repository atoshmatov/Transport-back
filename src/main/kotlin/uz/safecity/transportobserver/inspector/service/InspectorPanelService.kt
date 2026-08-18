package uz.safecity.transportobserver.inspector.service

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.checkpoints.repository.CheckpointRepository
import uz.safecity.transportobserver.common.exception.ForbiddenException
import uz.safecity.transportobserver.incidents.entity.ActionType
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.entity.InspectionStatus
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import uz.safecity.transportobserver.inspector.dto.ActionTypeCountDto
import uz.safecity.transportobserver.inspector.dto.DashboardSummaryDto
import uz.safecity.transportobserver.inspector.dto.IncidentTypeCountDto
import uz.safecity.transportobserver.inspector.dto.InspectorCurrentLocationDto
import uz.safecity.transportobserver.inspector.dto.InspectorStatsDto
import uz.safecity.transportobserver.inspector.dto.RecentActivityDto
import uz.safecity.transportobserver.inspector.dto.RecentInspectionDto
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

/**
 * Backs the Inspector web-panel dashboard + map screens
 * (`GET /api/v1/inspector/dashboard/summary`, `GET /api/v1/inspector/map/current-location`).
 *
 * [getDashboardSummary] is now backed by the real `inspections` module
 * ([uz.safecity.transportobserver.inspections.entity.Inspection]) rather than
 * `Incident` — that was a deliberate placeholder from an earlier phase (see
 * git history) because no Inspection module existed yet; see that entity's
 * kdoc for why Incident/Inspection are different concepts entirely
 * (unplanned field reports vs. planned, admin-assigned tasks) and must not
 * share a data source. `openInspectionsCount`/`completedTodayCount`/
 * `recentInspections` all reuse the same `Inspection.assignedInspectorId ==
 * principal.accountId` scoping as
 * [uz.safecity.transportobserver.inspections.service.InspectionService], so
 * an inspector can never see another inspector's counts/recent items through
 * this panel either.
 *
 * [getCurrentLocation] is intentionally left on `Incident` for its
 * [InspectorCurrentLocationDto.lastUpdatedAt] fallback — it is a location/map
 * screen, not an inspection-scheduling screen, and there is still no
 * Inspector-Checkpoint assignment mechanism to source a real "current
 * checkpoint" from either way (see below). Do not read this as an
 * inconsistency: the two endpoints answer different questions.
 *
 * The `checkpoints` module now exists ([uz.safecity.transportobserver.checkpoints]),
 * so [DashboardSummaryDto.activeCheckpointsCount] is a real count
 * (`CheckpointRepository.countByIsActiveTrue`) — it is deliberately the
 * system-wide count, not scoped to "this inspector's" checkpoints, because
 * there is still no Inspector-Checkpoint assignment mechanism anywhere in
 * the codebase (no column/table linking an inspector account to "their"
 * checkpoint). For the same reason, [InspectorCurrentLocationDto.checkpointName]/
 * `lat`/`lng` still return `null` — inventing a "nearest checkpoint to the
 * inspector's last incident" heuristic would produce plausible-looking but
 * arbitrary data, not real data. TODO (next task): once product defines how
 * an inspector gets attached to a checkpoint (e.g. a `checkpoint_id` on
 * Account, or a join table), source these three fields from that instead.
 */
@Service
class InspectorPanelService(
	private val incidentRepository: IncidentRepository,
	private val inspectionRepository: InspectionRepository,
	private val checkpointRepository: CheckpointRepository
) {

	fun getDashboardSummary(principal: CustomUserDetails): DashboardSummaryDto {
		assertInspector(principal.role)
		val accountId = principal.accountId

		val openCount = inspectionRepository.countByAssignedInspectorIdAndStatusIn(
			accountId,
			listOf(InspectionStatus.PLANNED, InspectionStatus.IN_PROGRESS)
		)
		val (startOfToday, endOfToday) = todayRange()
		val completedTodayCount = inspectionRepository.countByAssignedInspectorIdAndStatusAndPerformedAtBetween(
			accountId,
			InspectionStatus.COMPLETED,
			startOfToday,
			endOfToday
		)

		// Ordered by updatedAt desc, so this doubles as "most recently touched task" for lastUpdatedAt below.
		val recent = inspectionRepository.findTop5ByAssignedInspectorIdOrderByUpdatedAtDesc(accountId)
		val checkpointsById = checkpointRepository.findAllById(recent.map { it.checkpointId }).associateBy { requireNotNull(it.id) }
		val recentInspections = recent.map { RecentInspectionDto.from(it, checkpointsById[it.checkpointId]?.name) }

		return DashboardSummaryDto(
			openInspectionsCount = openCount.toInt(),
			completedTodayCount = completedTodayCount.toInt(),
			// System-wide active checkpoint count — see class kdoc re: no per-inspector assignment yet.
			activeCheckpointsCount = checkpointRepository.countByIsActiveTrue().toInt(),
			lastUpdatedAt = recent.firstOrNull()?.updatedAt ?: Instant.now(),
			recentInspections = recentInspections
		)
	}

	fun getCurrentLocation(principal: CustomUserDetails): InspectorCurrentLocationDto {
		val assigned = assignedIncidents(principal)

		return InspectorCurrentLocationDto(
			// No Inspector-Checkpoint assignment mechanism yet — see class kdoc.
			checkpointName = null,
			lat = null,
			lng = null,
			lastUpdatedAt = latestUpdatedAt(assigned)
		)
	}

	/**
	 * `GET /api/v1/inspector/me/stats` — see [InspectorStatsDto] kdoc for why
	 * [InspectorStatsDto.totalInspectionsCount] and the 4 action counts come from two different
	 * repositories/modules. The action counts reuse [countGroupByActionTypeForInspector]'s single
	 * grouped query (already computed for [getActionTypeBreakdown]) rather than 4 separate
	 * `COUNT` calls.
	 */
	fun getMyStats(principal: CustomUserDetails): InspectorStatsDto {
		assertInspector(principal.role)
		val accountId = principal.accountId

		val totalInspectionsCount = inspectionRepository.countByAssignedInspectorIdAndStatus(
			accountId,
			InspectionStatus.COMPLETED
		)

		val actionCounts = incidentRepository.countGroupByActionTypeForInspector(accountId)
			.mapNotNull { row -> row.actionType?.let { it to row.cnt } }
			.toMap()

		return InspectorStatsDto(
			totalInspectionsCount = totalInspectionsCount.toInt(),
			violationsRecordedCount = (actionCounts[ActionType.VIOLATION_RECORDED] ?: 0L).toInt(),
			warningsGivenCount = (actionCounts[ActionType.WARNING_GIVEN] ?: 0L).toInt(),
			photoEvidenceSubmittedCount = (actionCounts[ActionType.PHOTO_EVIDENCE_SUBMITTED] ?: 0L).toInt(),
			dangersReportedCount = (actionCounts[ActionType.DANGER_REPORTED] ?: 0L).toInt()
		)
	}

	/**
	 * `GET /api/v1/inspector/me/incident-type-breakdown` — see [IncidentTypeCountDto] kdoc for why
	 * every [IncidentType] value is always represented (zero-filled), not just the ones with data.
	 */
	fun getIncidentTypeBreakdown(principal: CustomUserDetails): List<IncidentTypeCountDto> {
		assertInspector(principal.role)

		val countsByType = incidentRepository.countGroupByTypeForInspector(principal.accountId)
			.associate { it.type to it.cnt }

		return IncidentType.entries.map { type -> IncidentTypeCountDto(type, countsByType[type] ?: 0L) }
	}

	/**
	 * `GET /api/v1/inspector/me/action-type-breakdown` — see [ActionTypeCountDto] kdoc for why
	 * legacy null-`actionType` incidents are excluded rather than bucketed as "unspecified".
	 */
	fun getActionTypeBreakdown(principal: CustomUserDetails): List<ActionTypeCountDto> {
		assertInspector(principal.role)

		val countsByAction = incidentRepository.countGroupByActionTypeForInspector(principal.accountId)
			.mapNotNull { row -> row.actionType?.let { it to row.cnt } }
			.toMap()

		return ActionType.entries.map { action -> ActionTypeCountDto(action, countsByAction[action] ?: 0L) }
	}

	/** `GET /api/v1/inspector/me/recent-activity` — see [RecentActivityDto] kdoc. */
	fun getRecentActivity(principal: CustomUserDetails): List<RecentActivityDto> {
		assertInspector(principal.role)

		return incidentRepository.findTop10ByAssignedInspectorIdOrderByCreatedAtDesc(principal.accountId)
			.map { RecentActivityDto.from(it) }
	}

	private fun assignedIncidents(principal: CustomUserDetails): List<Incident> {
		assertInspector(principal.role)
		return incidentRepository.findByAssignedInspectorId(principal.accountId)
	}

	private fun latestUpdatedAt(incidents: List<Incident>): Instant =
		incidents.maxOfOrNull { it.updatedAt ?: Instant.EPOCH }
			?.takeIf { it != Instant.EPOCH }
			?: Instant.now()

	/** [Pair.first] = start of today (inclusive), [Pair.second] = start of tomorrow (exclusive), in [APP_ZONE]. */
	private fun todayRange(): Pair<Instant, Instant> {
		val today = Instant.now().atZone(APP_ZONE).toLocalDate()
		val start = today.atStartOfDay(APP_ZONE).toInstant()
		val end = today.plusDays(1).atStartOfDay(APP_ZONE).toInstant()
		return start to end
	}

	/**
	 * Defense-in-depth mirror of the controller's @PreAuthorize — same pattern
	 * as IncidentService.assertCanAssignInspector, so this stays safe even if
	 * called from somewhere that forgot the annotation. This panel is
	 * INSPECTOR-only by design (it's scoped to "my" data via accountId), so
	 * unlike IncidentService.list there is no other-role branch here at all.
	 */
	private fun assertInspector(role: RoleType) {
		if (role != RoleType.INSPECTOR) {
			throw ForbiddenException("error.inspector.panel-forbidden")
		}
	}

	companion object {
		/**
		 * Fixed to the operating region rather than `ZoneId.systemDefault()`:
		 * this app is deployed in Docker (see docker-compose.yml), where the
		 * container's JVM default zone is typically UTC, not the inspectors'
		 * local time. Using the host default here would silently shift the
		 * "today" day-boundary for [getDashboardSummary]'s completedTodayCount
		 * by the UTC offset, undercounting/overcounting inspections completed
		 * near local midnight. No other file in this codebase does zone-aware
		 * date math yet, so there's no existing shared constant to reuse.
		 */
		private val APP_ZONE: ZoneId = ZoneId.of("Asia/Tashkent")
	}
}

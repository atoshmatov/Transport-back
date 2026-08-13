package uz.safecity.transportobserver.incidents.dto

import java.util.UUID

/**
 * Batch "evidence count per incident" projection for [uz.safecity.transportobserver.incidents.service.IncidentService.list] —
 * one grouped query for a whole page instead of N+1 (mirrors how
 * [uz.safecity.transportobserver.map.service.MapService.listLatestLocations] batches its
 * vehicle lookup). See [uz.safecity.transportobserver.incidents.repository.EvidenceRepository.countGroupByIncidentIds].
 */
interface EvidenceCountProjection {
	val incidentId: UUID
	val cnt: Long
}

package uz.safecity.transportobserver.incidents.repository

import uz.safecity.transportobserver.incidents.dto.EvidenceCountProjection
import uz.safecity.transportobserver.incidents.entity.Evidence
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EvidenceRepository : JpaRepository<Evidence, UUID> {

	/** Newest-first gallery order for `GET /incidents/{id}/evidence` (see EvidenceService.list). */
	fun findByIncidentIdOrderByCreatedAtDesc(incidentId: UUID): List<Evidence>

	/** Single-incident count — used by IncidentService for the one-row DTOs (getById/create/assign/status). */
	fun countByIncidentId(incidentId: UUID): Long

	/**
	 * Batch count for a whole page of incidents (IncidentService#list) — see
	 * [uz.safecity.transportobserver.incidents.dto.EvidenceCountProjection] kdoc for why this
	 * exists instead of calling [countByIncidentId] once per row.
	 */
	@Query("select e.incidentId as incidentId, count(e) as cnt from Evidence e where e.incidentId in :incidentIds group by e.incidentId")
	fun countGroupByIncidentIds(@Param("incidentIds") incidentIds: Collection<UUID>): List<EvidenceCountProjection>
}

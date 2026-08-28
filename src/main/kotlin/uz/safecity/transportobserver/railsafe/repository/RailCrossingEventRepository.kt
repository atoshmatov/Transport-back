package uz.safecity.transportobserver.railsafe.repository

import uz.safecity.transportobserver.railsafe.entity.RailCrossingEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface RailCrossingEventRepository : JpaRepository<RailCrossingEvent, UUID> {

	/** `POST /reports` RAILSAFE_EVENTS generation (ReportGenerationService) — every event detected within the report's `[periodStart, periodEnd)` window. */
	fun findByDetectedAtBetween(start: Instant, end: Instant): List<RailCrossingEvent>
}

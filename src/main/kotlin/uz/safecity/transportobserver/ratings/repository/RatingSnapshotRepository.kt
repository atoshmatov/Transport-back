package uz.safecity.transportobserver.ratings.repository

import uz.safecity.transportobserver.ratings.entity.RatingSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RatingSnapshotRepository : JpaRepository<RatingSnapshot, UUID> {

	/** `GET /api/v1/ratings/me/history` — chronological (oldest first) so the mobile chart can draw it left-to-right as-is. */
	fun findByAccountIdOrderBySnapshotDateAsc(accountId: UUID): List<RatingSnapshot>
}

package uz.safecity.transportobserver.incidents.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.locationtech.jts.geom.Point
import java.time.Instant
import java.util.UUID

/**
 * A single photo proof attached to an [Incident] (TZ section 6 `evidence` table: `incident_id,
 * file_url, file_type, captured_at, location`). [fileKey] is deliberately the MinIO *object
 * key*, never a raw URL — the bucket is private (TZ section 9), so a browsable URL is generated
 * on every read as a short-lived presigned GET (see
 * [uz.safecity.transportobserver.common.storage.FileStorageService]), not stored here where it
 * would just go stale.
 *
 * No `@ManyToOne` to [Incident] on purpose — every other cross-entity reference in this module
 * (`Incident.assignedInspectorId`, `Incident.reportedBy`) is a plain id column rather than a
 * managed association, so this follows the same convention rather than introducing the only
 * lazy-loading relationship in the package.
 */
@Entity
@Table(name = "evidence")
class Evidence(

	@Column(name = "incident_id", nullable = false)
	var incidentId: UUID,

	@Column(name = "file_key", nullable = false, length = 512)
	var fileKey: String,

	@Column(name = "file_type", nullable = false, length = 100)
	var fileType: String,

	@Column(name = "file_size_bytes", nullable = false)
	var fileSizeBytes: Long,

	/** When the photo was actually taken, if the client supplies it — falls back to upload time otherwise. */
	@Column(name = "captured_at")
	var capturedAt: Instant? = null,

	/** Optional — a photo can carry its own GPS point distinct from the parent incident's. */
	@Column(columnDefinition = "geometry(Point,4326)")
	var location: Point? = null,

	/** Account id of the uploader (usually the assigned INSPECTOR, but admin/operator may also attach evidence). */
	@Column(name = "uploaded_by")
	var uploadedBy: UUID? = null

) : BaseEntity()

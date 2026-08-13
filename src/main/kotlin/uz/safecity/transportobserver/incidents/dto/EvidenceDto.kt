package uz.safecity.transportobserver.incidents.dto

import uz.safecity.transportobserver.incidents.entity.Evidence
import java.time.Instant
import java.util.UUID

/**
 * [fileUrl] is a short-lived presigned GET (see
 * [uz.safecity.transportobserver.common.storage.FileStorageService]) generated fresh on every
 * read, never persisted — the bucket is private (TZ section 9), so there is no stable public
 * URL to hand out.
 */
data class EvidenceDto(
	val id: UUID,
	val incidentId: UUID,
	val fileUrl: String,
	val fileType: String,
	val fileSizeBytes: Long,
	val capturedAt: Instant?,
	val latitude: Double?,
	val longitude: Double?,
	val uploadedBy: UUID?,
	val createdAt: Instant?
) {
	companion object {
		fun from(evidence: Evidence, fileUrl: String) = EvidenceDto(
			id = requireNotNull(evidence.id),
			incidentId = evidence.incidentId,
			fileUrl = fileUrl,
			fileType = evidence.fileType,
			fileSizeBytes = evidence.fileSizeBytes,
			capturedAt = evidence.capturedAt,
			latitude = evidence.location?.y,
			longitude = evidence.location?.x,
			uploadedBy = evidence.uploadedBy,
			createdAt = evidence.createdAt
		)
	}
}

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

/**
 * `POST /incidents/{id}/evidence` response when the caller uploads through the multi-photo
 * `files` param (see [uz.safecity.transportobserver.incidents.controller.IncidentController.uploadEvidence]
 * kdoc for why this is a SEPARATE response shape from the single-file `file` param's plain
 * [EvidenceDto]). Deliberately a per-file PARTIAL-success result, not an all-or-nothing
 * transaction — see [uz.safecity.transportobserver.incidents.service.EvidenceService.uploadBatch]
 * kdoc: one bad photo (wrong format, too large) in a multi-photo batch must not discard the
 * other, valid photos in the same request.
 */
data class EvidenceUploadResultDto(
	val uploaded: List<EvidenceDto>,
	val failed: List<EvidenceUploadFailureDto>
)

/** One rejected file within an [EvidenceUploadResultDto] — [message] is the same localized text a single-file upload would have gotten back as its error response. */
data class EvidenceUploadFailureDto(
	val fileName: String?,
	val message: String?
)

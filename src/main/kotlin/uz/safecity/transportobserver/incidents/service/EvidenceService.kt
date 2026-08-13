package uz.safecity.transportobserver.incidents.service

import uz.safecity.transportobserver.audit.service.AuditService
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.common.storage.FileStorageService
import uz.safecity.transportobserver.common.util.GeoUtils
import uz.safecity.transportobserver.incidents.dto.EvidenceDto
import uz.safecity.transportobserver.incidents.entity.Evidence
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.repository.EvidenceRepository
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

/**
 * Photo-evidence upload for an incident (TZ section 6 `evidence` table + section 9 "Fayl
 * xavfsizligi"). Kept as its own service rather than folded into [IncidentService] — it owns a
 * distinct concern (file validation + object storage) — while reusing IncidentService's
 * INSPECTOR-ownership rule ([findAccessible]) so a foreign incident id 404s the same way
 * everywhere in this module (see [IncidentService.getById] kdoc for the same "404, never 403"
 * reasoning).
 *
 * MIME/size validation implements TZ section 9 verbatim: only image/jpeg and image/png, capped
 * at [MAX_FILE_SIZE_BYTES]. The declared `Content-Type` is checked, but a client can lie about
 * it, so [sniffImageType] additionally checks the first bytes against each format's magic
 * number (JPEG: FF D8 FF, PNG: 89 50 4E 47) — a cheap server-side check for just two formats
 * that avoids pulling in a full MIME-sniffing library (e.g. Apache Tika) for this.
 *
 * Two-bosqichli presigned-upload flow (client uploads straight to MinIO) is intentionally
 * skipped for this pass per the task's own priority call: direct multipart through the backend
 * is simpler to ship correctly right now, and nothing here stops swapping the upload leg for a
 * presigned PUT later without touching [Evidence]/[EvidenceDto] at all — the object-storage
 * boundary is already isolated in [FileStorageService].
 */
@Service
class EvidenceService(
	private val incidentRepository: IncidentRepository,
	private val evidenceRepository: EvidenceRepository,
	private val fileStorageService: FileStorageService,
	private val auditService: AuditService
) {

	@Transactional
	fun upload(
		incidentId: UUID,
		file: MultipartFile,
		capturedAt: Instant?,
		latitude: Double?,
		longitude: Double?,
		principal: CustomUserDetails
	): EvidenceDto {
		findAccessible(incidentId, principal)

		if (file.isEmpty) throw BadRequestException("Fayl bo'sh")
		if (file.size > MAX_FILE_SIZE_BYTES) {
			throw BadRequestException("Fayl hajmi ${MAX_FILE_SIZE_BYTES / (1024 * 1024)}MB dan oshmasligi kerak")
		}

		val bytes = file.bytes
		val sniffedType = sniffImageType(bytes)
			?: throw BadRequestException("Faqat JPEG yoki PNG rasm fayllariga ruxsat berilgan")
		// Belt-and-suspenders: the declared Content-Type (if present) must also claim to be one
		// of the two allowed types — catches an obviously mislabeled upload even though the
		// magic-number check above is the real gate.
		if (file.contentType != null && file.contentType !in ALLOWED_MIME_TYPES) {
			throw BadRequestException("Faqat JPEG yoki PNG rasm fayllariga ruxsat berilgan")
		}

		val extension = if (sniffedType == "image/png") "png" else "jpg"
		val objectKey = "incidents/$incidentId/${UUID.randomUUID()}.$extension"
		fileStorageService.upload(objectKey, bytes, sniffedType)

		val evidence = evidenceRepository.save(
			Evidence(
				incidentId = incidentId,
				fileKey = objectKey,
				fileType = sniffedType,
				fileSizeBytes = bytes.size.toLong(),
				capturedAt = capturedAt ?: Instant.now(),
				location = GeoUtils.toPointOrNull(latitude, longitude),
				uploadedBy = principal.accountId
			)
		)

		auditService.record(
			actorAccountId = principal.accountId,
			action = "INCIDENT_EVIDENCE_UPLOADED",
			entityType = "Incident",
			entityId = incidentId,
			metadata = "evidenceId=${evidence.id};fileType=$sniffedType;sizeBytes=${bytes.size}"
		)

		return EvidenceDto.from(evidence, fileStorageService.presignedGetUrl(evidence.fileKey))
	}

	fun list(incidentId: UUID, principal: CustomUserDetails): List<EvidenceDto> {
		findAccessible(incidentId, principal)
		return evidenceRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
			.map { EvidenceDto.from(it, fileStorageService.presignedGetUrl(it.fileKey)) }
	}

	/**
	 * Same ownership rule as [IncidentService.getById]: an INSPECTOR only sees/touches its own
	 * assigned incident (404 on a foreign id, never 403 — never confirms a foreign id exists);
	 * SUPER_ADMIN/ADMIN/OPERATOR may touch any incident.
	 */
	private fun findAccessible(incidentId: UUID, principal: CustomUserDetails): Incident {
		val incident = if (principal.role == RoleType.INSPECTOR) {
			incidentRepository.findByIdAndAssignedInspectorId(incidentId, principal.accountId)
		} else {
			incidentRepository.findById(incidentId)
		}
		return incident.orElseThrow { ResourceNotFoundException("Hodisa topilmadi: $incidentId") }
	}

	/** Returns the sniffed MIME type ("image/jpeg"/"image/png"), or null if neither magic number matches. */
	private fun sniffImageType(bytes: ByteArray): String? = when {
		bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
		bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
			bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
		else -> null
	}

	companion object {
		const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
		private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png")
	}
}

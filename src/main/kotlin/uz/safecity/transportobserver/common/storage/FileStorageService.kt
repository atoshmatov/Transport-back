package uz.safecity.transportobserver.common.storage

import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.http.Method
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around [MinioClient] — object upload + presigned GET URLs (see
 * [uz.safecity.transportobserver.common.config.MinioConfig] kdoc: "Bucket creation/lifecycle
 * is left to a future FileStorageService" — this is that service). Generic on purpose so any
 * future module (report attachments, employee documents — same kdoc) can reuse it, not just
 * incident evidence.
 *
 * The bucket is kept PRIVATE (TZ section 9 "MinIO private bucket + presigned URL") — there is
 * no public-read policy anywhere here. Every read goes through [presignedGetUrl], a short-lived
 * signed link generated fresh per request and never persisted (persisting a presigned URL would
 * just store an expired link a few minutes later).
 */
@Service
class FileStorageService(
	private val minioClient: MinioClient,
	@Value("\${storage.minio.bucket}") private val bucket: String
) {

	/**
	 * Idempotent check-then-create, called lazily on first upload rather than at app startup —
	 * so the app still boots even if MinIO isn't up yet (e.g. docker-compose service ordering in
	 * dev); it only becomes a hard failure the first time someone actually uploads a file.
	 */
	private fun ensureBucket() {
		val exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
		if (!exists) {
			minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
		}
	}

	/** [objectKey] should already be namespaced by caller (e.g. `incidents/{id}/{uuid}.jpg`). Returns [objectKey] as-is for convenience chaining. */
	fun upload(objectKey: String, bytes: ByteArray, contentType: String): String {
		ensureBucket()
		ByteArrayInputStream(bytes).use { stream ->
			minioClient.putObject(
				PutObjectArgs.builder()
					.bucket(bucket)
					.`object`(objectKey)
					.stream(stream, bytes.size.toLong(), -1)
					.contentType(contentType)
					.build()
			)
		}
		return objectKey
	}

	/** Signed GET URL, valid for [expiryMinutes] (default 15) — private bucket stays private. */
	fun presignedGetUrl(objectKey: String, expiryMinutes: Int = 15): String =
		minioClient.getPresignedObjectUrl(
			GetPresignedObjectUrlArgs.builder()
				.method(Method.GET)
				.bucket(bucket)
				.`object`(objectKey)
				.expiry(expiryMinutes, TimeUnit.MINUTES)
				.build()
		)
}

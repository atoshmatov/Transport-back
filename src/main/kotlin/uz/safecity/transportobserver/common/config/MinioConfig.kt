package uz.safecity.transportobserver.common.config

import io.minio.MinioClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MinIO (S3-compatible) client for file storage — incident photos/videos,
 * report attachments, employee documents. Bucket creation/lifecycle is left
 * to a future FileStorageService; this only wires the client.
 */
@Configuration
class MinioConfig(
	@Value("\${storage.minio.endpoint}") private val endpoint: String,
	@Value("\${storage.minio.access-key}") private val accessKey: String,
	@Value("\${storage.minio.secret-key}") private val secretKey: String
) {

	@Bean
	fun minioClient(): MinioClient =
		MinioClient.builder()
			.endpoint(endpoint)
			.credentials(accessKey, secretKey)
			.build()
}

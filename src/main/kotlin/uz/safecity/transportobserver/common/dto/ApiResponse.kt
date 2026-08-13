package uz.safecity.transportobserver.common.dto

import java.time.Instant

/**
 * Uniform success envelope for every REST response in the system, so mobile
 * (Android/iOS) and the web admin panel can rely on one shape.
 */
data class ApiResponse<T>(
	val success: Boolean = true,
	val data: T? = null,
	val timestamp: Instant = Instant.now()
) {
	companion object {
		fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
	}
}

/** Uniform error envelope. See [uz.safecity.transportobserver.common.exception.GlobalExceptionHandler]. */
data class ErrorResponse(
	val success: Boolean = false,
	val code: String,
	val message: String?,
	val details: Map<String, String?> = emptyMap(),
	val timestamp: Instant = Instant.now()
)

/** Standard paginated wrapper for list endpoints (employees, incidents, reports, ...). */
data class PageResponse<T>(
	val content: List<T>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int
)

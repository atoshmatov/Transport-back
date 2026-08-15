package uz.safecity.transportobserver.common.exception

import uz.safecity.transportobserver.common.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import jakarta.servlet.http.HttpServletRequest

/**
 * Central place that turns exceptions into the [ErrorResponse] envelope.
 * Keep controllers/services free of try/catch for expected domain errors —
 * throw one of [ApiException]'s subtypes instead.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

	private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

	/**
	 * 4xx [ApiException]s (e.g. [ResourceNotFoundException] "... topilmadi") were previously
	 * silent server-side — only [ex.status.is5xxServerError] was logged, so a client-visible
	 * "ID topilmadi" error (wrong id sent by a caller, e.g. an account id used where an employee
	 * id was expected on the photo-upload endpoint) left no trace in the backend log, making it
	 * impossible to tell from the server side which id/endpoint actually 404'd after the fact.
	 * Logging every 4xx at WARN with method+path closes that gap without changing the response.
	 */
	@ExceptionHandler(ApiException::class)
	fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
		if (ex.status.is5xxServerError) {
			log.error("API error: {}", ex.message, ex)
		} else {
			log.warn("API error: {} {} -> {} {}", request.method, request.requestURI, ex.status.value(), ex.message)
		}
		return ResponseEntity.status(ex.status).body(
			ErrorResponse(code = ex.code, message = ex.message)
		)
	}

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
		val details = ex.bindingResult.allErrors.associate {
			val field = (it as? FieldError)?.field ?: it.objectName
			field to it.defaultMessage
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
			ErrorResponse(code = "VALIDATION_ERROR", message = "Kiritilgan ma'lumotlar noto'g'ri", details = details)
		)
	}

	@ExceptionHandler(BadCredentialsException::class)
	fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ErrorResponse> =
		ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
			ErrorResponse(code = "INVALID_CREDENTIALS", message = "Login yoki parol noto'g'ri")
		)

	/**
	 * Backstop for unique-constraint races (e.g. two concurrent employee
	 * creates picking the same username) that slip past the service layer's
	 * proactive `existsByUsername` check. Handles the general
	 * DataIntegrityViolationException family so any DB constraint violation
	 * comes back as 409 instead of a raw 500.
	 */
	@ExceptionHandler(DataIntegrityViolationException::class)
	fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
		log.warn("Data integrity violation: {}", ex.message)
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
			ErrorResponse(code = "CONFLICT", message = "Ma'lumot allaqachon mavjud yoki bog'liqlik cheklovi buzilgan")
		)
	}

	/**
	 * Thrown by Spring's multipart parsing itself (before EvidenceService ever runs) when a
	 * request exceeds `spring.servlet.multipart.max-*` (application.yml). EvidenceService's own
	 * MAX_FILE_SIZE_BYTES check produces the same BAD_REQUEST shape for sizes under that outer
	 * cap — this just covers the rarer "request-wide" ceiling in the same shape instead of a
	 * raw 500.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException::class)
	fun handleMaxUploadSize(ex: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
		ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
			ErrorResponse(code = "BAD_REQUEST", message = "Fayl hajmi ruxsat etilgan chegaradan katta")
		)

	@ExceptionHandler(AccessDeniedException::class)
	fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
		ResponseEntity.status(HttpStatus.FORBIDDEN).body(
			ErrorResponse(code = "FORBIDDEN", message = "Bu amalni bajarishga ruxsatingiz yo'q")
		)

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
		log.error("Unhandled exception", ex)
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
			ErrorResponse(code = "INTERNAL_ERROR", message = "Kutilmagan xatolik yuz berdi")
		)
	}
}

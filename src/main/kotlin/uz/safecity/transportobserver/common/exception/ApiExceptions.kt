package uz.safecity.transportobserver.common.exception

import org.springframework.http.HttpStatus

/**
 * Base type for every exception that should be translated into a structured
 * JSON error response by [uz.safecity.transportobserver.common.exception.GlobalExceptionHandler].
 */
sealed class ApiException(
	val status: HttpStatus,
	val code: String,
	message: String
) : RuntimeException(message)

class ResourceNotFoundException(
	message: String
) : ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message)

class BadRequestException(
	message: String
) : ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message)

class ConflictException(
	message: String
) : ApiException(HttpStatus.CONFLICT, "CONFLICT", message)

/** Wrong username/password, or a token that doesn't validate. */
class InvalidCredentialsException(
	message: String = "Login yoki parol noto'g'ri"
) : ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message)

/** Account temporarily locked after too many failed login attempts. */
class AccountLockedException(
	message: String = "Hisob vaqtincha bloklangan. Keyinroq urinib ko'ring"
) : ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", message)

/** Account deactivated by an administrator. */
class AccountDisabledException(
	message: String = "Hisob faol emas"
) : ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", message)

/** must_change_password = true and the caller tried to hit an endpoint other than /auth/change-password. */
class PasswordChangeRequiredException(
	message: String = "Avval parolni almashtirish kerak"
) : ApiException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", message)

class RefreshTokenInvalidException(
	message: String = "Refresh token yaroqsiz yoki muddati o'tgan"
) : ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", message)

class ForbiddenException(
	message: String = "Bu amalni bajarishga ruxsatingiz yo'q"
) : ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message)

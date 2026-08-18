package uz.safecity.transportobserver.common.exception

import org.springframework.http.HttpStatus

/**
 * Base type for every exception that should be translated into a structured
 * JSON error response by [uz.safecity.transportobserver.common.exception.GlobalExceptionHandler].
 *
 * [messageKey] + [args] are resolved into the final display text via [Messages] (backed by
 * `messages.properties`, see that file's header) — callers should pass a `error.<domain>.<case>`
 * key, e.g. `ResourceNotFoundException("error.employee.not-found", id)`, not a literal string.
 * A key with no matching properties entry (e.g. legacy call sites not yet migrated, see that
 * commit's notes) still works exactly as before: [Messages.resolve] falls back to returning the
 * string as-is, so passing plain Uzbek text here remains harmless, just not centralized/i18n-able.
 */
sealed class ApiException(
	val status: HttpStatus,
	val code: String,
	val messageKey: String,
	vararg args: Any?
) : RuntimeException(Messages.resolve(messageKey, *args))

class ResourceNotFoundException(
	messageKey: String,
	vararg args: Any?
) : ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", messageKey, *args)

class BadRequestException(
	messageKey: String,
	vararg args: Any?
) : ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", messageKey, *args)

class ConflictException(
	messageKey: String,
	vararg args: Any?
) : ApiException(HttpStatus.CONFLICT, "CONFLICT", messageKey, *args)

/** Wrong username/password, or a token that doesn't validate. */
class InvalidCredentialsException(
	messageKey: String = "error.auth.invalid-credentials",
	vararg args: Any?
) : ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", messageKey, *args)

/** Account temporarily locked after too many failed login attempts. */
class AccountLockedException(
	messageKey: String = "error.auth.account-locked",
	vararg args: Any?
) : ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", messageKey, *args)

/** Account deactivated by an administrator. */
class AccountDisabledException(
	messageKey: String = "error.auth.account-disabled",
	vararg args: Any?
) : ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", messageKey, *args)

/** must_change_password = true and the caller tried to hit an endpoint other than /auth/change-password. */
class PasswordChangeRequiredException(
	messageKey: String = "error.auth.password-change-required",
	vararg args: Any?
) : ApiException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", messageKey, *args)

class RefreshTokenInvalidException(
	messageKey: String = "error.auth.refresh-token-invalid",
	vararg args: Any?
) : ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", messageKey, *args)

class ForbiddenException(
	messageKey: String = "error.common.forbidden",
	vararg args: Any?
) : ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", messageKey, *args)

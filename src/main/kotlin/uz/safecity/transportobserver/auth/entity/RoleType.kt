package uz.safecity.transportobserver.auth.entity

/**
 * RBAC roles per TZ. Kept as a plain enum (not a DB table) for MVP — simple,
 * fast to check in @PreAuthorize, and matches the fixed 4-role set defined
 * in the spec. Promote to a `roles` table later only if roles need to become
 * dynamic/configurable per tenant.
 */
enum class RoleType {
	SUPER_ADMIN,
	ADMIN,
	OPERATOR,
	INSPECTOR;

	/** Spring Security expects the "ROLE_" prefix on granted authorities. */
	val authority: String get() = "ROLE_$name"
}

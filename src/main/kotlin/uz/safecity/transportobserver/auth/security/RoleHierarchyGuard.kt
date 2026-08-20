package uz.safecity.transportobserver.auth.security

import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.common.exception.ForbiddenException

/**
 * Shared role-hierarchy gate for account provisioning/management actions (create employee,
 * block/activate, password reset). Extracted from [uz.safecity.transportobserver.employees.service.EmployeeService]
 * (formerly a private `assertCanManageRole` there) so it can also be applied by
 * [uz.safecity.transportobserver.auth.service.AuthService.resetPassword] — the generic
 * accountId-scoped password-reset path reachable via `POST /api/v1/auth/reset-password`.
 *
 * Before this extraction, that generic path had no hierarchy check at all: it was guarded only by
 * the controller-level `@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")`, which
 * admits ADMIN but says nothing about *whose* account ADMIN may act on. That let an ADMIN reset the
 * password of another ADMIN or of SUPER_ADMIN simply by calling this endpoint directly with an
 * arbitrary `accountId` — bypassing the exact privilege-escalation protection already enforced on
 * the Employee-scoped endpoint (`POST /api/v1/admin/employees/{id}/reset-password`). Keeping the
 * rule in one shared place means every current and future entry point to "manage this account" uses
 * the identical check instead of two independently-maintained copies that can drift apart.
 *
 * SUPER_ADMIN may manage any role. ADMIN may only manage OPERATOR/INSPECTOR accounts — never
 * SUPER_ADMIN or (any) ADMIN, including ADMIN accounts it did not itself create. This is a domain
 * rule, independent of the controller-level `@PreAuthorize` gate that merely admits SUPER_ADMIN/ADMIN
 * into these endpoints in the first place.
 */
object RoleHierarchyGuard {

    /**
     * [targetOrRequestedRole] is either the role being assigned to a new account (create) or the
     * role already held by the account being acted on (updateStatus/resetPassword).
     */
    fun assertCanManage(actorRole: RoleType, targetOrRequestedRole: RoleType) {
        if (actorRole == RoleType.SUPER_ADMIN) return

        if (actorRole == RoleType.ADMIN &&
            (targetOrRequestedRole == RoleType.SUPER_ADMIN || targetOrRequestedRole == RoleType.ADMIN)
        ) {
            throw ForbiddenException("error.employee.role-create-forbidden")
        }
    }
}

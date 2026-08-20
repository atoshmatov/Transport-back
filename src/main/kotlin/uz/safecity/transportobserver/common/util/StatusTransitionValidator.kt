package uz.safecity.transportobserver.common.util

/**
 * Shared status-transition (state-machine) enforcement for every domain that has a
 * PATCH-able `status` field (currently [uz.safecity.transportobserver.incidents.service.IncidentService]
 * and [uz.safecity.transportobserver.inspections.service.InspectionService]).
 *
 * Before this existed, `updateStatus` on both services accepted ANY target status from ANY
 * current status — including going straight back to the initial status (e.g.
 * `RESOLVED -> NEW`), which makes no domain sense and silently corrupts SLA/reporting
 * metrics that assume a status only moves forward through the workflow. This is a generic
 * `Map<T, Set<T>>` graph walker rather than one bespoke `when` per enum so both call sites
 * share one tested implementation instead of two copies that could drift.
 *
 * [wildcardTargets] models a target status reachable from ANY current status regardless of
 * the graph (e.g. [uz.safecity.transportobserver.inspections.entity.InspectionStatus.CANCELLED]
 * — an admin/operator can cancel a PLANNED, IN_PROGRESS, or even already-COMPLETED inspection).
 * It intentionally does NOT bypass the [current] == [target] no-op check's caller-visible
 * behavior — that is handled uniformly by the same early-return.
 */
object StatusTransitionValidator {

    /**
     * @param current the entity's status before this call.
     * @param target the status the caller is requesting.
     * @param allowedTransitions adjacency list: `current -> set of targets reachable in one hop`.
     *   A `current` with no entry (or an empty set) is treated as a dead end (no forward moves).
     * @param wildcardTargets targets that are always allowed regardless of [current] — see class kdoc.
     * @param bypass when true (SUPER_ADMIN/ADMIN "fix a mistake" escape hatch — see call sites),
     *   the graph is not consulted at all: any transition is allowed.
     * @param onInvalid invoked (and expected to throw) when the transition is neither a no-op,
     *   a wildcard target, a bypass, nor present in [allowedTransitions].
     */
    fun <T> assertAllowed(
        current: T,
        target: T,
        allowedTransitions: Map<T, Set<T>>,
        wildcardTargets: Set<T> = emptySet(),
        bypass: Boolean,
        onInvalid: () -> Nothing
    ) {
        if (bypass) return
        if (current == target) return
        if (target in wildcardTargets) return
        val reachable = allowedTransitions[current] ?: emptySet()
        if (target !in reachable) {
            onInvalid()
        }
    }
}

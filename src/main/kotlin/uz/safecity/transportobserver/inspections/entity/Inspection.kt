package uz.safecity.transportobserver.inspections.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class InspectionStatus { PLANNED, IN_PROGRESS, COMPLETED, CANCELLED }

/**
 * A PLANNED, admin/operator-assigned inspection task against a [Checkpoint]
 * (TZ section 6, "Nazorat/transport") — e.g. "shu nazorat punktini shu sanada
 * tekshir". This is a deliberately DIFFERENT concept from
 * [uz.safecity.transportobserver.incidents.entity.Incident]: an Incident is a
 * violation/event a field inspector *reports* from the mobile app (bottom-up,
 * unplanned), while an Inspection is a task an admin/operator *schedules and
 * hands to* an inspector (top-down, planned) — see IncidentService kdoc for
 * the sibling scoping pattern this module mirrors.
 *
 * [assignedInspectorId] deliberately points at
 * [uz.safecity.transportobserver.auth.entity.Account.id], NOT
 * [uz.safecity.transportobserver.employees.entity.Employee.id] — same
 * reasoning as [uz.safecity.transportobserver.incidents.entity.Incident.assignedInspectorId]:
 * this is an *authentication*-bound "who performs this as the logged-in
 * INSPECTOR" relationship (every scoping check compares this column directly
 * against `CustomUserDetails.accountId`, the JWT principal, on every
 * request), not an *organizational* "who operates this asset" relationship.
 * Contrast with [uz.safecity.transportobserver.vehicles.entity.Vehicle.assignedEmployeeId],
 * which uses Employee.id because it answers "which employee drives this
 * vehicle" — a roster fact with no bearing on request-time authorization.
 * Going through Employee here would mean an extra Account lookup on every
 * scoping check for no benefit, since an inspector's account is 1:1 with
 * their employee row anyway.
 */
@Entity
@Table(name = "inspections")
class Inspection(

	@Column(name = "checkpoint_id", nullable = false)
	var checkpointId: UUID,

	/** Null = not yet assigned to an inspector. See class kdoc for why this is an Account id. */
	@Column(name = "assigned_inspector_id")
	var assignedInspectorId: UUID? = null,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var status: InspectionStatus = InspectionStatus.PLANNED,

	/** When the admin/operator wants this inspection carried out. */
	@Column(name = "scheduled_at")
	var scheduledAt: Instant? = null,

	/** When the inspection was actually carried out — auto-filled on transition to COMPLETED, see InspectionService. */
	@Column(name = "performed_at")
	var performedAt: Instant? = null,

	/** Free-form task description (set at creation) and/or outcome notes (filled in by the inspector on completion). */
	@Column(columnDefinition = "text")
	var notes: String? = null,

	/** The SUPER_ADMIN/ADMIN/OPERATOR account that created/assigned this inspection. */
	@Column(name = "created_by")
	var createdBy: UUID? = null,

	/**
	 * "Elektron" inspector signature per design's `TO-Screen.dc.html` `reportDetail` ("TASDIQ VA
	 * IMZOLAR" -> "Inspektor imzosi ... Elektron · <vaqt>"). NOT a cryptographic/PKI signature —
	 * there is no keypair, no signed hash, nothing verifiable offline. It is simply the
	 * server-recorded timestamp of "the authenticated inspector confirmed this inspection's
	 * completion at this moment", auto-set by
	 * [uz.safecity.transportobserver.inspections.service.InspectionService]#updateStatus the moment
	 * the inspection transitions to [InspectionStatus.COMPLETED]. Do not present this field to
	 * users or auditors as providing non-repudiation guarantees stronger than "this account's
	 * session did this, at this time" — the same guarantee any other audited write in this system
	 * already has.
	 */
	@Column(name = "inspector_signed_at")
	var inspectorSignedAt: Instant? = null,

	/**
	 * "Elektron" driver signature — same non-cryptographic "timestamp confirmation" meaning as
	 * [inspectorSignedAt] (see its kdoc), but for the driver being inspected. The driver has no
	 * account/session in this system, so there is nothing to authenticate here beyond the
	 * inspector's own device recording "the driver confirmed in person at this moment" — set only
	 * when the completion request explicitly asks for it
	 * ([uz.safecity.transportobserver.inspections.dto.UpdateInspectionStatusRequest.driverConfirmed]).
	 */
	@Column(name = "driver_signed_at")
	var driverSignedAt: Instant? = null,

	/** Optional witness name per design's "TASDIQ VA IMZOLAR" -> "Guvoh (ixtiyoriy)" — plain text, no signature/timestamp of its own. */
	@Column(name = "witness_name")
	var witnessName: String? = null

) : BaseEntity()

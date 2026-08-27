package uz.safecity.transportobserver.incidents.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One row per meaningful transition in an [Incident]'s life — backs the mobile "Hodisa kartasi"
 * status-history timeline (`TO-Screen.dc.html` incidentDetail: "Qayd etildi -> Markaziy tizimga
 * yuborildi -> Boshqarma tomonidan ko'rildi -> Chora ko'rilmoqda"). Append-only (never
 * updated/deleted) — this IS the audit trail, written by
 * [uz.safecity.transportobserver.incidents.service.IncidentService] at every state-changing call
 * (`create`/`createSos`/`assignInspector`/`updateStatus`/`updateResolution`/`cancelSos`), not
 * reconstructed after the fact from other tables.
 *
 * [status] mirrors [IncidentStatus] only when the event corresponds 1:1 to a real status value
 * (e.g. "Qayd etildi" <-> [IncidentStatus.NEW]). Several timeline steps from the mobile design
 * ("Markaziy tizimga yuborildi", "Boshqarma tomonidan ko'rildi", "Chora ko'rilmoqda") are workflow
 * milestones with no corresponding [IncidentStatus] value — [status] is left null for those and
 * [label] alone carries the meaning, rather than inventing [IncidentStatus] values the state
 * machine in IncidentService has no other use for.
 *
 * [actorAccountId] is null for system-triggered steps (e.g. the automatic "Markaziy tizimga
 * yuborildi" entry written right after an SOS auto-broadcasts to admins) — there is no human actor
 * to record for those.
 */
@Entity
@Table(name = "incident_status_events")
class IncidentStatusEvent(

	@Column(name = "incident_id", nullable = false)
	var incidentId: UUID,

	/** Human-readable timeline label — see class kdoc for why this (not just [status]) carries the meaning. */
	@Column(nullable = false, length = 128)
	var label: String,

	/** Real [IncidentStatus] this event corresponds to, if any — see class kdoc. */
	@Enumerated(EnumType.STRING)
	@Column(length = 32)
	var status: IncidentStatus? = null,

	/** [uz.safecity.transportobserver.auth.entity.Account.id] of whoever triggered this step; null for system-triggered steps. */
	@Column(name = "actor_account_id")
	var actorAccountId: UUID? = null,

	@Column(name = "occurred_at", nullable = false)
	var occurredAt: Instant = Instant.now()

) : BaseEntity()

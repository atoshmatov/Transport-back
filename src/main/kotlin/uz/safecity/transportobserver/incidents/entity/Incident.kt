package uz.safecity.transportobserver.incidents.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.locationtech.jts.geom.Point
import java.time.Instant
import java.util.UUID

enum class IncidentStatus { NEW, IN_PROGRESS, RESOLVED, REJECTED }
enum class IncidentType { ACCIDENT, VIOLATION, TECHNICAL_FAULT, SECURITY, FIRE, MEDICAL, OTHER }

/**
 * What the inspector actually DID when filing this [Incident] — orthogonal to [IncidentType]
 * (which is WHAT happened / what kind of situation was observed). Added for the mobile Profile
 * screen's "harakat turi bo'yicha" donut breakdown + the 5-metric `GET
 * /api/v1/inspector/me/stats` (see [uz.safecity.transportobserver.inspector.service.InspectorPanelService]
 * kdoc), which previously had no server-side data at all and was hardcoded on the client.
 *
 * Nullable on the entity ([Incident.actionType]) rather than a required column with a Flyway
 * backfill: this app has no Flyway/Liquibase migrations yet (`ddl-auto: update`, see
 * application-dev.yml TODO), and every [Incident] row created before this field existed has no
 * sensible default action to backfill to (guessing one would fabricate data, not recover it).
 * MVP tradeoff, not an oversight — see [uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest]
 * kdoc for the client-facing side of this, and
 * [uz.safecity.transportobserver.inspector.service.InspectorPanelService.getActionTypeBreakdown]
 * for how legacy null rows are excluded from the breakdown rather than fabricated into a bucket.
 */
enum class ActionType { VIOLATION_RECORDED, WARNING_GIVEN, PHOTO_EVIDENCE_SUBMITTED, DANGER_REPORTED }

/**
 * Placeholder — PostGIS-backed geolocation via Hibernate Spatial (`Point`,
 * SRID 4326 / WGS84). Full schema (attachments, assigned inspector, SLA
 * timestamps, etc.) comes from TZ section 6 in the next implementation pass.
 */
@Entity
@Table(name = "incidents")
class Incident(

	@Column(nullable = false)
	var title: String,

	@Column(columnDefinition = "text")
	var description: String? = null,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var type: IncidentType,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var status: IncidentStatus = IncidentStatus.NEW,

	/** What the inspector did — see [ActionType] kdoc for why this is nullable. */
	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", length = 32)
	var actionType: ActionType? = null,

	@Column(columnDefinition = "geometry(Point,4326)")
	var location: Point? = null,

	@Column(name = "reported_by")
	var reportedBy: UUID? = null,

	@Column(name = "occurred_at")
	var occurredAt: Instant? = null,

	/**
	 * The INSPECTOR account (see [uz.safecity.transportobserver.auth.entity.Account.id],
	 * not [uz.safecity.transportobserver.employees.entity.Employee.id]) this incident is
	 * assigned to. Deliberately points at the *account* id rather than the employee id:
	 * scoping checks compare this column directly against
	 * `CustomUserDetails.accountId` (the JWT principal) on every request, and
	 * going through Employee would mean an extra Account lookup per check for
	 * no benefit — an inspector's account is 1:1 with their employee row anyway.
	 *
	 * Null = unassigned; only SUPER_ADMIN/ADMIN/OPERATOR can see/manage
	 * unassigned or other-inspectors' incidents (see IncidentService).
	 */
	@Column(name = "assigned_inspector_id")
	var assignedInspectorId: UUID? = null,

	// TODO (region module): plain text field until a real `regions` table exists —
	// see Employee/Checkpoint kdoc for the same pattern. Lets admin/operator filter
	// the incident board by region ahead of that module landing.
	@Column(name = "region_name")
	var regionName: String? = null,

	/**
	 * Client-generated UUID for offline dedup (TZ section 6 `client_uuid` / section 8.2
	 * "local DB is source of truth, client_uuid for offline dedup"). The mobile app creates
	 * this locally before the incident ever leaves the device, so a retried offline-sync
	 * POST /incidents with the same clientUuid resolves to the already-created row instead
	 * of inserting a duplicate — see IncidentService#create. Unique but nullable: an
	 * incident created directly (e.g. Operator manual entry, TZ permission matrix "qo'lda")
	 * has no client device and leaves this null; the unique constraint therefore only
	 * applies to non-null values (standard SQL unique-index semantics).
	 */
	@Column(name = "client_uuid", unique = true)
	var clientUuid: UUID? = null,

	/**
	 * Marks this row as having been created through the inspector's SOS / favqulodda signal
	 * (`POST /api/v1/inspector/me/sos`) rather than a normal incident report — orthogonal to
	 * [status]: an SOS incident carries whatever [type] the mobile "Holat turi" picker sent
	 * (defaults to [IncidentType.OTHER] when omitted by an older client — see
	 * [uz.safecity.transportobserver.incidents.dto.CreateSosRequest] kdoc) plus a fixed
	 * [ActionType.DANGER_REPORTED] + [IncidentStatus.NEW] (see
	 * [uz.safecity.transportobserver.incidents.service.IncidentService.createSos] kdoc for why a
	 * brand-new status was deliberately NOT added), so this flag is the only server-side signal
	 * that distinguishes "inspector pressed the panic button" from an ordinary report of the same [type].
	 * Also gates [uz.safecity.transportobserver.incidents.service.IncidentService.cancelSos]: only
	 * an `isSos=true` row within its 5-second cancel window may be cancelled that way.
	 */
	@Column(name = "is_sos", nullable = false)
	var isSos: Boolean = false

) : BaseEntity()

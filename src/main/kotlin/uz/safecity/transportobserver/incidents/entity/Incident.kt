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
enum class IncidentType { ACCIDENT, VIOLATION, TECHNICAL_FAULT, SECURITY, OTHER }

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
	var clientUuid: UUID? = null

) : BaseEntity()

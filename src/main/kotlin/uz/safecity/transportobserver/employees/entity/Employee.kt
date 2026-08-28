package uz.safecity.transportobserver.employees.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDate

enum class EmployeeStatus { ACTIVE, INACTIVE, DISMISSED }

/**
 * TODO (region module): [regionName] is a plain text field for now — TZ section 6
 * calls for a proper `regions` table (regionId FK), but that module doesn't exist
 * yet. Once it lands, migrate this column to `region_id` + a `@ManyToOne Region`
 * and backfill existing rows by name match.
 *
 * An [uz.safecity.transportobserver.auth.entity.Account] references this row via
 * `employee_id` (plain FK column, not a mapped JPA relation — see Account kdoc).
 *
 * The HR "full profile" fields below ([personalId] through [assignedBadgeCameraId]) back the
 * mobile/web "Xodim kartasi" (`profileDetail`) design, which shows this data alongside what the
 * class already had — see [uz.safecity.transportobserver.inspector.dto.ProfileDetailDto] kdoc for
 * the screen this feeds. All of them are nullable/optional: existing employee rows have none of
 * this entered yet, and there is (as of this addition) still no admin-panel form to fill it in —
 * that UI is a separate, later task. Never backfilled or guessed; a `null` here means "not entered
 * yet", not "known to be empty".
 */
@Entity
@Table(name = "employees")
class Employee(

	@Column(name = "full_name", nullable = false)
	var fullName: String,

	@Column
	var position: String? = null,

	@Column
	var department: String? = null,

	@Column(name = "region_name")
	var regionName: String? = null,

	@Column(name = "phone_number")
	var phoneNumber: String? = null,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var status: EmployeeStatus = EmployeeStatus.ACTIVE,

	@Column(name = "hired_at")
	var hiredAt: LocalDate? = null,

	@Column(name = "photo_key")
	var photoKey: String? = null,

	/** JSHSHIR (Uzbekistan personal ID number) or passport series+number — plain text, no format enforced here. */
	@Column(name = "personal_id")
	var personalId: String? = null,

	@Column(name = "birth_date")
	var birthDate: LocalDate? = null,

	@Column(name = "home_address", columnDefinition = "text")
	var homeAddress: String? = null,

	@Column
	var email: String? = null,

	@Column(name = "service_certificate_number")
	var serviceCertificateNumber: String? = null,

	@Column(name = "service_certificate_expires_at")
	var serviceCertificateExpiresAt: LocalDate? = null,

	/** e.g. "B", "BC", "D" — free text, no fixed enum since the driving-category set is a DMV/GAI concern, not this app's. */
	@Column(name = "driver_license_category")
	var driverLicenseCategory: String? = null,

	@Column(name = "last_certification_at")
	var lastCertificationAt: LocalDate? = null,

	/**
	 * Inventory identifier of the tablet/badge-camera physically handed to this employee — a
	 * plain free-text identifier, NOT a foreign key: this codebase has no equipment/inventory
	 * table yet (checked before adding this — see [uz.safecity.transportobserver.vehicles.entity.Vehicle]
	 * for the one piece of "assigned hardware" that IS a real entity today, which is deliberately
	 * left as-is and unrelated to these two fields). Should a real equipment-inventory module land
	 * later, these can be migrated to FK columns the same way the region TODO above describes.
	 */
	@Column(name = "assigned_tablet_id")
	var assignedTabletId: String? = null,

	@Column(name = "assigned_badge_camera_id")
	var assignedBadgeCameraId: String? = null

) : BaseEntity()

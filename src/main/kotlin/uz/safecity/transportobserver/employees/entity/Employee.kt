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
	var photoKey: String? = null

) : BaseEntity()

package uz.safecity.transportobserver.checkpointtypes.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Admin-managed reference list of checkpoint categories (e.g. "Avtovokzal" /
 * "Temiryo'l vokzali" / "Magistral yo'l" / "Aeroport") — same "reference-data
 * CRUD" shape as [uz.safecity.transportobserver.positions.entity.EmployeePosition]
 * / [uz.safecity.transportobserver.regions.entity.Region]: a flat admin-editable
 * lookup table, chosen from a dropdown rather than free-typed.
 *
 * Referenced by [uz.safecity.transportobserver.checkpoints.entity.Checkpoint.checkpointTypeId]
 * (plain FK column, not a mapped JPA relation — same convention as
 * [uz.safecity.transportobserver.auth.entity.Account.employeeId]). See that
 * field's kdoc for why the legacy `Checkpoint.type` free-text column still
 * exists alongside it.
 */
@Entity
@Table(name = "checkpoint_types")
class CheckpointType(

	@Column(nullable = false, unique = true)
	var name: String,

	@Column(columnDefinition = "text")
	var description: String? = null

) : BaseEntity()

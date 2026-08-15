package uz.safecity.transportobserver.regions.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "regions")
class Region(
	@Column(nullable = false, unique = true)
	var name: String,

	@Column
	var code: String? = null
) : BaseEntity()

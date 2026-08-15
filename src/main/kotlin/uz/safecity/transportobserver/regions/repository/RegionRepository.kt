package uz.safecity.transportobserver.regions.repository

import uz.safecity.transportobserver.regions.entity.Region
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RegionRepository : JpaRepository<Region, UUID> {
	fun existsByName(name: String): Boolean
}

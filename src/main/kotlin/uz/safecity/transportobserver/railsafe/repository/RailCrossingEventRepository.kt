package uz.safecity.transportobserver.railsafe.repository

import uz.safecity.transportobserver.railsafe.entity.RailCrossingEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RailCrossingEventRepository : JpaRepository<RailCrossingEvent, UUID>

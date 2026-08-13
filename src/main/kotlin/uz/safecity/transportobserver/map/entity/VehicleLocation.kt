package uz.safecity.transportobserver.map.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.locationtech.jts.geom.Point
import java.time.Instant
import java.util.UUID

/**
 * Latest known position of a tracked entity (vehicle/inspector device).
 * Placeholder table — real-time updates are expected to flow in over
 * WebSocket (see common.config.WebSocketConfig) and/or a periodic upsert
 * from a mobile client, not raw JPA CRUD from the browser.
 */
@Entity
@Table(name = "vehicle_locations")
class VehicleLocation(

	@Column(name = "vehicle_id", nullable = false)
	var vehicleId: UUID,

	@Column(columnDefinition = "geometry(Point,4326)", nullable = false)
	var location: Point,

	@Column
	var speedKmh: Double? = null,

	@Column(name = "recorded_at", nullable = false)
	var recordedAt: Instant = Instant.now()

) : BaseEntity()

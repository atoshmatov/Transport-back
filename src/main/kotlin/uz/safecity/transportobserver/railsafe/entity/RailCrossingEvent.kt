package uz.safecity.transportobserver.railsafe.entity

import uz.safecity.transportobserver.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.locationtech.jts.geom.Point
import java.time.Instant

enum class RailEventType { TRAIN_APPROACH, BARRIER_DOWN, BARRIER_UP, VIOLATION, SENSOR_FAULT }
enum class RailEventSeverity { INFO, WARNING, CRITICAL }

/**
 * Placeholder for the railway-crossing safety monitoring domain (`railsafe`).
 * Real ingestion is expected to come from external sensors/CV pipeline
 * (Safecity FastAPI side) via RabbitMQ, not manual REST writes.
 */
@Entity
@Table(name = "railsafe_events")
class RailCrossingEvent(

	@Column(name = "crossing_code", nullable = false)
	var crossingCode: String,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	var eventType: RailEventType,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	var severity: RailEventSeverity = RailEventSeverity.INFO,

	@Column(columnDefinition = "geometry(Point,4326)")
	var location: Point? = null,

	@Column(name = "detected_at", nullable = false)
	var detectedAt: Instant = Instant.now()

) : BaseEntity()

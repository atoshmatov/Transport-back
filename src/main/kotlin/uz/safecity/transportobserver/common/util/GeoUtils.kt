package uz.safecity.transportobserver.common.util

import uz.safecity.transportobserver.common.exception.BadRequestException
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.PrecisionModel

/**
 * Shared WGS84 (SRID 4326) point builder + range validation for every module that accepts a
 * lat/lng pair from a client (Checkpoint/Incident/Evidence/...). Kept as a plain object
 * (not injected) since it's stateless besides the immutable [GeometryFactory] — matches how
 * CheckpointService already builds points, just de-duplicated for the two new call sites
 * (Incident, Evidence) added alongside offline incident-reporting + photo evidence.
 *
 * [toPointOrNull] additionally allows both lat/lng to be absent — Incident/Evidence reports
 * may be GPS-less per TZ 8.3 "GPS o'chirilgan holat: hodisa GPS'siz ham yuborilishi mumkin" —
 * while still rejecting a half-supplied pair (one present, one missing) as a client bug.
 */
object GeoUtils {
	private val GEOMETRY_FACTORY = GeometryFactory(PrecisionModel(), 4326)

	fun toPoint(latitude: Double, longitude: Double): Point {
		validateRange(latitude, longitude)
		return GEOMETRY_FACTORY.createPoint(Coordinate(longitude, latitude))
	}

	/** Null only when BOTH are null; a half-supplied pair is a [BadRequestException]. */
	fun toPointOrNull(latitude: Double?, longitude: Double?): Point? {
		if (latitude == null && longitude == null) return null
		val lat = latitude ?: throw BadRequestException("error.geo.latitude-required")
		val lng = longitude ?: throw BadRequestException("error.geo.longitude-required")
		return toPoint(lat, lng)
	}

	private fun validateRange(latitude: Double, longitude: Double) {
		if (latitude < -90.0 || latitude > 90.0) throw BadRequestException("error.geo.invalid-latitude")
		if (longitude < -180.0 || longitude > 180.0) throw BadRequestException("error.geo.invalid-longitude")
	}
}

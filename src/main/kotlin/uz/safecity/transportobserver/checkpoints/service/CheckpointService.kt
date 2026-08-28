package uz.safecity.transportobserver.checkpoints.service

import uz.safecity.transportobserver.checkpoints.dto.CheckpointDto
import uz.safecity.transportobserver.checkpoints.dto.CheckpointNearbyDto
import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.dto.UpdateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import uz.safecity.transportobserver.checkpoints.repository.CheckpointRepository
import uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType
import uz.safecity.transportobserver.checkpointtypes.repository.CheckpointTypeRepository
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.common.util.GeoUtils
import jakarta.persistence.criteria.Predicate
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CheckpointService(
	private val checkpointRepository: CheckpointRepository,
	private val checkpointTypeRepository: CheckpointTypeRepository
) {

	fun list(
		regionName: String?,
		type: String?,
		isActive: Boolean?,
		checkpointTypeId: UUID?,
		pageable: Pageable
	): PageResponse<CheckpointDto> {
		val page = checkpointRepository.findAll(buildSpecification(regionName, type, isActive, checkpointTypeId), pageable)
		val typesById = checkpointTypesById(page.content)
		return PageResponse(
			content = page.content.map { CheckpointDto.from(it, typesById[it.checkpointTypeId]) },
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages
		)
	}

	fun getById(id: UUID): CheckpointDto {
		val checkpoint = findOrThrow(id)
		return CheckpointDto.from(checkpoint, checkpoint.checkpointTypeId?.let { checkpointTypeRepository.findById(it).orElse(null) })
	}

	/**
	 * Consumed by [uz.safecity.transportobserver.map.controller.MapController]'s
	 * `GET /api/v1/map/checkpoints` — every authenticated role (incl. INSPECTOR)
	 * may see active checkpoints on the map, unlike [list] which is Admin/Operator only.
	 */
	fun listActiveForMap(): List<CheckpointDto> {
		val checkpoints = checkpointRepository.findByIsActiveTrue()
		val typesById = checkpointTypesById(checkpoints)
		return checkpoints.map { CheckpointDto.from(it, typesById[it.checkpointTypeId]) }
	}

	/** Consumed by InspectorPanelService for `DashboardSummaryDto.activeCheckpointsCount`. */
	fun countActive(): Long = checkpointRepository.countByIsActiveTrue()

	/**
	 * `GET /api/v1/checkpoints/nearby` — the mobile client's nearest-checkpoint SUGGESTION list for
	 * the "hodisa yaratish" flow (see [uz.safecity.transportobserver.incidents.entity.Incident.checkpointId]
	 * kdoc for the full hybrid-approach reasoning). Purely a read: [checkpointRepository.findNearestActive]
	 * ranks active checkpoints by real PostGIS distance, nothing here decides or persists anything
	 * on the caller's behalf.
	 *
	 * [limit] is clamped to `[1, 50]` — a client-supplied value outside that range (0, negative, or
	 * an unreasonably large number) is defensively normalized rather than rejected, since this is a
	 * convenience list, not a data-integrity-sensitive write.
	 *
	 * Reuses [GeoUtils.toPoint] purely for its shared lat/lng range validation (same
	 * `error.geo.invalid-latitude`/`error.geo.invalid-longitude` as every other module accepting a
	 * client-supplied point — see that object's kdoc); the [org.locationtech.jts.geom.Point] it
	 * builds is discarded; the actual distance math happens in the native query.
	 */
	fun findNearby(latitude: Double, longitude: Double, limit: Int): List<CheckpointNearbyDto> {
		GeoUtils.toPoint(latitude, longitude)
		val effectiveLimit = limit.coerceIn(1, 50)
		return checkpointRepository.findNearestActive(latitude, longitude, effectiveLimit)
			.map { CheckpointNearbyDto.from(it) }
	}

	@Transactional
	fun create(request: CreateCheckpointRequest): CheckpointDto {
		val checkpointType = resolveCheckpointType(request.checkpointTypeId)
		@Suppress("DEPRECATION")
		val checkpoint = Checkpoint(
			name = request.name,
			regionName = request.regionName,
			location = toPoint(request.latitude, request.longitude),
			description = request.description,
			type = request.type,
			checkpointTypeId = request.checkpointTypeId
		)
		return CheckpointDto.from(checkpointRepository.save(checkpoint), checkpointType)
	}

	@Transactional
	fun update(id: UUID, request: UpdateCheckpointRequest): CheckpointDto {
		val checkpoint = findOrThrow(id)
		val checkpointType = resolveCheckpointType(request.checkpointTypeId)
		checkpoint.name = request.name
		checkpoint.regionName = request.regionName
		checkpoint.location = toPoint(request.latitude, request.longitude)
		checkpoint.description = request.description
		@Suppress("DEPRECATION")
		run { checkpoint.type = request.type }
		checkpoint.checkpointTypeId = request.checkpointTypeId
		return CheckpointDto.from(checkpointRepository.save(checkpoint), checkpointType)
	}

	/** Soft-deactivate only — see [Checkpoint] kdoc re: why there is no hard-delete endpoint. */
	@Transactional
	fun updateStatus(id: UUID, isActive: Boolean): CheckpointDto {
		val checkpoint = findOrThrow(id)
		checkpoint.isActive = isActive
		val saved = checkpointRepository.save(checkpoint)
		return CheckpointDto.from(saved, saved.checkpointTypeId?.let { checkpointTypeRepository.findById(it).orElse(null) })
	}

	private fun findOrThrow(id: UUID): Checkpoint =
		checkpointRepository.findById(id).orElseThrow { ResourceNotFoundException("error.checkpoint.not-found", id) }

	/** Validates the admin-selected type exists (404 otherwise); `null` is allowed — see [Checkpoint.checkpointTypeId] kdoc. */
	private fun resolveCheckpointType(checkpointTypeId: UUID?): CheckpointType? =
		checkpointTypeId?.let {
			checkpointTypeRepository.findById(it)
				.orElseThrow { ResourceNotFoundException("error.checkpoint-type.not-found", it) }
		}

	/** Batched lookup for list/map enrichment — one query per page instead of one per row (same pattern as EmployeeService's onDutyIds batching). */
	private fun checkpointTypesById(checkpoints: List<Checkpoint>): Map<UUID, CheckpointType> {
		val ids = checkpoints.mapNotNull { it.checkpointTypeId }.toSet()
		return checkpointTypeRepository.findByIdIn(ids).associateBy { requireNotNull(it.id) }
	}

	private fun buildSpecification(
		regionName: String?,
		type: String?,
		isActive: Boolean?,
		checkpointTypeId: UUID?
	): Specification<Checkpoint> =
		Specification { root, _, cb ->
			val predicates = mutableListOf<Predicate>()
			regionName?.let { predicates.add(cb.equal(root.get<String>("regionName"), it)) }
			type?.let { predicates.add(cb.equal(root.get<String>("type"), it)) }
			isActive?.let { predicates.add(cb.equal(root.get<Boolean>("isActive"), it)) }
			checkpointTypeId?.let { predicates.add(cb.equal(root.get<UUID>("checkpointTypeId"), it)) }
			cb.and(*predicates.toTypedArray())
		}

	/** [latitude]/[longitude] are validated `@NotNull` by the request DTO; the range check here guards against out-of-bounds values that bean validation doesn't cover. */
	private fun toPoint(latitude: Double?, longitude: Double?): Point {
		val lat = requireNotNull(latitude) { "latitude majburiy" }
		val lng = requireNotNull(longitude) { "longitude majburiy" }
		if (lat < -90.0 || lat > 90.0) throw BadRequestException("error.geo.invalid-latitude")
		if (lng < -180.0 || lng > 180.0) throw BadRequestException("error.geo.invalid-longitude")
		return GEOMETRY_FACTORY.createPoint(Coordinate(lng, lat))
	}

	companion object {
		/** SRID 4326 / WGS84 — same convention as Incident/VehicleLocation/RailCrossingEvent. */
		private val GEOMETRY_FACTORY = GeometryFactory(PrecisionModel(), 4326)
	}
}

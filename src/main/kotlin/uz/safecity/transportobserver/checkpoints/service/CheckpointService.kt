package uz.safecity.transportobserver.checkpoints.service

import uz.safecity.transportobserver.checkpoints.dto.CheckpointDto
import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.dto.UpdateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.entity.Checkpoint
import uz.safecity.transportobserver.checkpoints.repository.CheckpointRepository
import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
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
	private val checkpointRepository: CheckpointRepository
) {

	fun list(regionName: String?, type: String?, isActive: Boolean?, pageable: Pageable): PageResponse<CheckpointDto> {
		val page = checkpointRepository.findAll(buildSpecification(regionName, type, isActive), pageable)
		return PageResponse(
			content = page.content.map { CheckpointDto.from(it) },
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages
		)
	}

	fun getById(id: UUID): CheckpointDto = CheckpointDto.from(findOrThrow(id))

	/**
	 * Consumed by [uz.safecity.transportobserver.map.controller.MapController]'s
	 * `GET /api/v1/map/checkpoints` — every authenticated role (incl. INSPECTOR)
	 * may see active checkpoints on the map, unlike [list] which is Admin/Operator only.
	 */
	fun listActiveForMap(): List<CheckpointDto> =
		checkpointRepository.findByIsActiveTrue().map { CheckpointDto.from(it) }

	/** Consumed by InspectorPanelService for `DashboardSummaryDto.activeCheckpointsCount`. */
	fun countActive(): Long = checkpointRepository.countByIsActiveTrue()

	@Transactional
	fun create(request: CreateCheckpointRequest): CheckpointDto {
		val checkpoint = Checkpoint(
			name = request.name,
			regionName = request.regionName,
			location = toPoint(request.latitude, request.longitude),
			description = request.description,
			type = request.type
		)
		return CheckpointDto.from(checkpointRepository.save(checkpoint))
	}

	@Transactional
	fun update(id: UUID, request: UpdateCheckpointRequest): CheckpointDto {
		val checkpoint = findOrThrow(id)
		checkpoint.name = request.name
		checkpoint.regionName = request.regionName
		checkpoint.location = toPoint(request.latitude, request.longitude)
		checkpoint.description = request.description
		checkpoint.type = request.type
		return CheckpointDto.from(checkpointRepository.save(checkpoint))
	}

	/** Soft-deactivate only — see [Checkpoint] kdoc re: why there is no hard-delete endpoint. */
	@Transactional
	fun updateStatus(id: UUID, isActive: Boolean): CheckpointDto {
		val checkpoint = findOrThrow(id)
		checkpoint.isActive = isActive
		return CheckpointDto.from(checkpointRepository.save(checkpoint))
	}

	private fun findOrThrow(id: UUID): Checkpoint =
		checkpointRepository.findById(id).orElseThrow { ResourceNotFoundException("error.checkpoint.not-found", id) }

	private fun buildSpecification(regionName: String?, type: String?, isActive: Boolean?): Specification<Checkpoint> =
		Specification { root, _, cb ->
			val predicates = mutableListOf<Predicate>()
			regionName?.let { predicates.add(cb.equal(root.get<String>("regionName"), it)) }
			type?.let { predicates.add(cb.equal(root.get<String>("type"), it)) }
			isActive?.let { predicates.add(cb.equal(root.get<Boolean>("isActive"), it)) }
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

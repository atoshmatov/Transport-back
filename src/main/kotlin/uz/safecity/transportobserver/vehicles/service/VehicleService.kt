package uz.safecity.transportobserver.vehicles.service

import uz.safecity.transportobserver.common.dto.PageResponse
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import uz.safecity.transportobserver.employees.entity.EmployeeStatus
import uz.safecity.transportobserver.employees.repository.EmployeeRepository
import uz.safecity.transportobserver.vehicles.dto.CreateVehicleRequest
import uz.safecity.transportobserver.vehicles.dto.UpdateVehicleRequest
import uz.safecity.transportobserver.vehicles.dto.VehicleDto
import uz.safecity.transportobserver.vehicles.dto.VehiclePickerDto
import uz.safecity.transportobserver.vehicles.entity.Vehicle
import uz.safecity.transportobserver.vehicles.entity.VehicleType
import uz.safecity.transportobserver.vehicles.repository.VehicleRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class VehicleService(
	private val vehicleRepository: VehicleRepository,
	private val employeeRepository: EmployeeRepository
) {

	fun list(
		type: VehicleType?,
		isActive: Boolean?,
		regionName: String?,
		assignedEmployeeId: UUID?,
		pageable: Pageable
	): PageResponse<VehicleDto> {
		val page = vehicleRepository.findAll(
			buildSpecification(type, isActive, regionName, assignedEmployeeId),
			pageable
		)
		return PageResponse(
			content = page.content.map { VehicleDto.from(it) },
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages
		)
	}

	fun getById(id: UUID): VehicleDto = VehicleDto.from(findOrThrow(id))

	/** Consumed by MapController/MapService if the `/map/vehicles` enrichment TODO is picked up — see VehicleRepository kdoc. */
	fun listActiveForMap(): List<VehicleDto> =
		vehicleRepository.findByIsActiveTrue().map { VehicleDto.from(it) }

	/**
	 * Backs `GET /api/v1/inspector/vehicles` — see
	 * [uz.safecity.transportobserver.inspector.controller.InspectorPanelController.listVehiclesForPicker]
	 * kdoc for why this is a separate, deliberately narrower sibling of [list]. Only
	 * [uz.safecity.transportobserver.vehicles.entity.Vehicle.isActive] rows are ever returned (a
	 * deactivated vehicle must not be selectable when reporting a new incident), and [query] — when
	 * non-blank — does a case-insensitive substring match against plate number OR model, so an
	 * inspector can find a vehicle either way without knowing which field they're typing into.
	 */
	fun listForInspectorPicker(query: String?, pageable: Pageable): PageResponse<VehiclePickerDto> {
		val page = vehicleRepository.findAll(buildPickerSpecification(query), pageable)
		return PageResponse(
			content = page.content.map { VehiclePickerDto.from(it) },
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages
		)
	}

	@Transactional
	fun create(request: CreateVehicleRequest): VehicleDto {
		val plateNumber = request.plateNumber.trim()
		assertPlateNumberAvailable(plateNumber)
		assertAssignedEmployeeIsActive(request.assignedEmployeeId)

		val vehicle = Vehicle(
			plateNumber = plateNumber,
			type = requireNotNull(request.type) { "type majburiy" },
			model = request.model,
			regionName = request.regionName,
			ownerType = request.ownerType,
			assignedEmployeeId = request.assignedEmployeeId
		)
		return VehicleDto.from(vehicleRepository.save(vehicle))
	}

	@Transactional
	fun update(id: UUID, request: UpdateVehicleRequest): VehicleDto {
		val vehicle = findOrThrow(id)
		val plateNumber = request.plateNumber.trim()
		assertPlateNumberAvailable(plateNumber, excludingId = id)
		assertAssignedEmployeeIsActive(request.assignedEmployeeId)

		vehicle.plateNumber = plateNumber
		vehicle.type = requireNotNull(request.type) { "type majburiy" }
		vehicle.model = request.model
		vehicle.regionName = request.regionName
		vehicle.ownerType = request.ownerType
		vehicle.assignedEmployeeId = request.assignedEmployeeId
		return VehicleDto.from(vehicleRepository.save(vehicle))
	}

	/** Soft-deactivate only — see [Vehicle] kdoc re: why there is no hard-delete endpoint. */
	@Transactional
	fun updateStatus(id: UUID, isActive: Boolean): VehicleDto {
		val vehicle = findOrThrow(id)
		vehicle.isActive = isActive
		return VehicleDto.from(vehicleRepository.save(vehicle))
	}

	private fun findOrThrow(id: UUID): Vehicle =
		vehicleRepository.findById(id).orElseThrow { ResourceNotFoundException("error.vehicle.not-found", id) }

	/** Field-level 409 message, same shape as EmployeeService's username-conflict check, so the frontend can parse it identically. */
	private fun assertPlateNumberAvailable(plateNumber: String, excludingId: UUID? = null) {
		val conflict = if (excludingId == null) {
			vehicleRepository.existsByPlateNumberIgnoreCase(plateNumber)
		} else {
			vehicleRepository.existsByPlateNumberIgnoreCaseAndIdNot(plateNumber, excludingId)
		}
		if (conflict) {
			throw ConflictException("error.vehicle.plate-taken", plateNumber)
		}
	}

	/** [assignedEmployeeId], when provided, must reference a real, currently-active [uz.safecity.transportobserver.employees.entity.Employee]. */
	private fun assertAssignedEmployeeIsActive(assignedEmployeeId: UUID?) {
		if (assignedEmployeeId == null) return
		val employee = employeeRepository.findById(assignedEmployeeId)
			.orElseThrow { BadRequestException("error.vehicle.assigned-employee-not-found", assignedEmployeeId) }
		if (employee.status != EmployeeStatus.ACTIVE) {
			throw BadRequestException("error.vehicle.assigned-employee-inactive", assignedEmployeeId)
		}
	}

	private fun buildSpecification(
		type: VehicleType?,
		isActive: Boolean?,
		regionName: String?,
		assignedEmployeeId: UUID?
	): Specification<Vehicle> =
		Specification { root, _, cb ->
			val predicates = mutableListOf<Predicate>()
			type?.let { predicates.add(cb.equal(root.get<VehicleType>("type"), it)) }
			isActive?.let { predicates.add(cb.equal(root.get<Boolean>("isActive"), it)) }
			regionName?.let { predicates.add(cb.equal(root.get<String>("regionName"), it)) }
			assignedEmployeeId?.let { predicates.add(cb.equal(root.get<UUID>("assignedEmployeeId"), it)) }
			cb.and(*predicates.toTypedArray())
		}

	/** [listForInspectorPicker]'s filter: always `isActive = true`, plus an optional plate/model substring match. */
	private fun buildPickerSpecification(query: String?): Specification<Vehicle> =
		Specification { root, _, cb ->
			val predicates = mutableListOf<Predicate>()
			predicates.add(cb.isTrue(root.get("isActive")))
			query?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
				val likePattern = "%${q.lowercase()}%"
				predicates.add(
					cb.or(
						cb.like(cb.lower(root.get<String>("plateNumber")), likePattern),
						cb.like(cb.lower(root.get<String>("model")), likePattern)
					)
				)
			}
			cb.and(*predicates.toTypedArray())
		}
}

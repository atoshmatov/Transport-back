package uz.safecity.transportobserver.employees.service

import uz.safecity.transportobserver.employees.dto.EmployeePositionHistoryDto
import uz.safecity.transportobserver.employees.entity.EmployeePositionHistory
import uz.safecity.transportobserver.employees.repository.EmployeePositionHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Maintains [EmployeePositionHistory] — the lavozim/hudud o'zgarish jurnali. The only writer is
 * [recordChange], called from [EmployeeService.create] (to seed the employee's first/current
 * spell) and [EmployeeService.update] (whenever `position`/`regionName` actually changes) — never
 * called directly from a controller, this is a side-effect of editing an [uz.safecity.transportobserver.employees.entity.Employee],
 * not its own CRUD resource.
 */
@Service
class EmployeePositionHistoryService(
	private val employeePositionHistoryRepository: EmployeePositionHistoryRepository
) {

	/**
	 * Closes the employee's current open spell (if any) by stamping its `endedAt`, then — only if
	 * [newPosition] is non-blank — opens a new one starting now. A blank/null [newPosition] (the
	 * position was cleared) still closes the old spell but deliberately does not open a
	 * replacement: [EmployeePositionHistory.position] is non-null, so there is nothing meaningful
	 * to record as "current" until a new position is set again.
	 *
	 * Safe to call even when nothing actually changed for the employee's very first spell (no open
	 * row exists yet, so the close step is a no-op) — this lets [EmployeeService.create] reuse the
	 * exact same method to seed the initial history row instead of duplicating this logic.
	 *
	 * The close step uses `saveAndFlush`, not `save`: Hibernate's default flush ordering runs all
	 * pending INSERTs before UPDATEs *regardless of the order they were issued in code* (action
	 * queue is grouped by operation type, not call order). Left as a plain `save`, the new row's
	 * INSERT (`ended_at IS NULL`) would hit the DB before this UPDATE closes the old one, so both
	 * rows would briefly violate [uz.safecity.transportobserver.employees.entity.EmployeePositionHistory]'s
	 * "at most one open row per employee" partial unique index — an immediate
	 * `DataIntegrityViolationException` on the very first real position change, caught by
	 * [uz.safecity.transportobserver.employees.service.EmployeePositionHistoryServiceTests].
	 * Forcing the close to flush first guarantees the UPDATE physically lands before the INSERT is
	 * even sent.
	 */
	@Transactional
	fun recordChange(employeeId: UUID, newPosition: String?, newRegionName: String?) {
		val now = Instant.now()

		employeePositionHistoryRepository.findByEmployeeIdAndEndedAtIsNull(employeeId)?.let { open ->
			open.endedAt = now
			employeePositionHistoryRepository.saveAndFlush(open)
		}

		val trimmedPosition = newPosition?.trim()?.takeIf { it.isNotEmpty() } ?: return
		employeePositionHistoryRepository.save(
			EmployeePositionHistory(
				employeeId = employeeId,
				position = trimmedPosition,
				regionName = newRegionName?.trim()?.takeIf { it.isNotEmpty() },
				startedAt = now
			)
		)
	}

	/** Full history for `GET /api/v1/admin/employees/{id}/position-history`, newest first. */
	fun list(employeeId: UUID): List<EmployeePositionHistoryDto> =
		employeePositionHistoryRepository.findByEmployeeIdOrderByStartedAtDesc(employeeId)
			.map { EmployeePositionHistoryDto.from(it) }
}

package uz.safecity.transportobserver.reports.service

import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.common.exception.BadRequestException
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import uz.safecity.transportobserver.inspections.entity.Inspection
import uz.safecity.transportobserver.inspections.repository.InspectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Covers `GET /api/v1/reports/activity?range=` — the "7d"/"30d" (daily) and "1y" (monthly)
 * windows added on top of the pre-existing "7d"-only implementation. Every row's `created_at` is
 * force-set via a direct SQL update (bypassing the `@CreatedDate` auditing listener, which always
 * stamps "now") so each test can place activity at an exact, deterministic calendar day/month
 * relative to "today" in `Asia/Tashkent` — the same zone [ReportStatsService] buckets by.
 */
@SpringBootTest
@Transactional
class ReportStatsServiceTests {

	@Autowired
	lateinit var reportStatsService: ReportStatsService

	@Autowired
	lateinit var inspectionRepository: InspectionRepository

	@Autowired
	lateinit var incidentRepository: IncidentRepository

	@Autowired
	lateinit var checkpointService: CheckpointService

	@Autowired
	lateinit var jdbcTemplate: JdbcTemplate

	private val zone = ZoneId.of("Asia/Tashkent")

	private fun checkpointId(): UUID =
		checkpointService.create(CreateCheckpointRequest(name = "Test CP ${UUID.randomUUID()}", latitude = 41.3, longitude = 69.2)).id

	/** Backdates an already-saved [Inspection]'s `created_at` to noon (Asia/Tashkent) on [day], bypassing @CreatedDate. */
	private fun backdateInspection(id: UUID, day: LocalDate) {
		val instant = day.atTime(12, 0).atZone(zone).toInstant()
		jdbcTemplate.update("update inspections set created_at = ? where id = ?", Timestamp.from(instant), id)
	}

	private fun backdateIncident(id: UUID, day: LocalDate) {
		val instant = day.atTime(12, 0).atZone(zone).toInstant()
		jdbcTemplate.update("update incidents set created_at = ? where id = ?", Timestamp.from(instant), id)
	}

	/** [saveAndFlush] (not [org.springframework.data.repository.CrudRepository.save]) — the row must actually be
	 * physically written before the raw JDBC backdate below touches it, otherwise the UPDATE silently
	 * matches zero rows (Hibernate's write-behind INSERT hasn't reached the DB yet) and the row keeps
	 * its real `@CreatedDate`-stamped "now" timestamp instead of [day]. */
	private fun saveInspectionOn(cpId: UUID, day: LocalDate) {
		val saved = inspectionRepository.saveAndFlush(Inspection(checkpointId = cpId))
		backdateInspection(requireNotNull(saved.id), day)
	}

	private fun saveIncidentOn(day: LocalDate) {
		val saved = incidentRepository.saveAndFlush(Incident(title = "T ${UUID.randomUUID()}", type = IncidentType.VIOLATION))
		backdateIncident(requireNotNull(saved.id), day)
	}

	@Test
	fun `getActivity rejects an unsupported range`() {
		assertThrows(BadRequestException::class.java) {
			reportStatsService.getActivity("3d")
		}
	}

	@Test
	fun `getActivity 7d returns one point per day for the last 7 days`() {
		val result = reportStatsService.getActivity("7d")
		assertEquals(7, result.size)
		assertEquals(LocalDate.now(zone), result.last().date)
		assertEquals(LocalDate.now(zone).minusDays(6), result.first().date)
	}

	@Test
	fun `getActivity 30d buckets daily counts and excludes activity outside the window`() {
		val cp = checkpointId()
		val today = LocalDate.now(zone)

		// This is the shared dev database (see CheckpointStatsServiceTests for the same caveat) —
		// other rows may already exist for "today" etc. from unrelated activity, so this asserts
		// the DELTA this test itself introduces, not an absolute count.
		val before = reportStatsService.getActivity("30d").associateBy { it.date }

		saveInspectionOn(cp, today) // inside window
		saveInspectionOn(cp, today.minusDays(29)) // oldest day still inside the 30-day window
		saveInspectionOn(cp, today.minusDays(35)) // outside the window -> must not be counted
		saveIncidentOn(today.minusDays(10)) // inside window

		val result = reportStatsService.getActivity("30d")

		assertEquals(30, result.size)
		assertEquals(today.minusDays(29), result.first().date)
		assertEquals(today, result.last().date)

		val byDate = result.associateBy { it.date }
		fun inspectionsDelta(day: LocalDate) = byDate.getValue(day).inspectionsCount - before.getValue(day).inspectionsCount
		fun incidentsDelta(day: LocalDate) = byDate.getValue(day).incidentsCount - before.getValue(day).incidentsCount

		assertEquals(1, inspectionsDelta(today))
		assertEquals(1, inspectionsDelta(today.minusDays(29)))
		assertEquals(1, incidentsDelta(today.minusDays(10)))
		// A day this test added nothing to must show zero delta (the point itself must still be
		// present in the series, just unaffected — see the "before" map lookup succeeding at all).
		assertEquals(0, inspectionsDelta(today.minusDays(5)))
		assertEquals(0, incidentsDelta(today.minusDays(5)))
	}

	@Test
	fun `getActivity 1y buckets counts by calendar month and excludes activity outside the window`() {
		val cp = checkpointId()
		val currentMonth = YearMonth.now(zone)

		// Same shared-dev-database caveat as the 30d test above — assert deltas, not absolutes.
		val before = reportStatsService.getActivity("1y").associateBy { it.date }

		saveInspectionOn(cp, currentMonth.atDay(1)) // this month -> inside window
		saveInspectionOn(cp, currentMonth.minusMonths(11).atDay(1)) // oldest month still inside the 12-month window
		saveInspectionOn(cp, currentMonth.minusMonths(13).atDay(1)) // outside the window -> must not be counted
		saveIncidentOn(currentMonth.minusMonths(3).atDay(1))

		val result = reportStatsService.getActivity("1y")

		assertEquals(12, result.size)
		assertEquals(currentMonth.minusMonths(11).atDay(1), result.first().date)
		assertEquals(currentMonth.atDay(1), result.last().date)

		val byMonth = result.associateBy { it.date }
		fun inspectionsDelta(month: LocalDate) = byMonth.getValue(month).inspectionsCount - before.getValue(month).inspectionsCount
		fun incidentsDelta(month: LocalDate) = byMonth.getValue(month).incidentsCount - before.getValue(month).incidentsCount

		assertEquals(1, inspectionsDelta(currentMonth.atDay(1)))
		assertEquals(1, inspectionsDelta(currentMonth.minusMonths(11).atDay(1)))
		assertEquals(1, incidentsDelta(currentMonth.minusMonths(3).atDay(1)))
		// A month this test added nothing to must show zero delta.
		assertEquals(0, inspectionsDelta(currentMonth.minusMonths(6).atDay(1)))
		assertEquals(0, incidentsDelta(currentMonth.minusMonths(6).atDay(1)))
	}
}

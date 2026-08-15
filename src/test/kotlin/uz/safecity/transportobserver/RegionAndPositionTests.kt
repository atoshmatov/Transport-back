package uz.safecity.transportobserver

import uz.safecity.transportobserver.positions.dto.CreateEmployeePositionRequest
import uz.safecity.transportobserver.positions.service.EmployeePositionService
import uz.safecity.transportobserver.regions.dto.CreateRegionRequest
import uz.safecity.transportobserver.regions.service.RegionService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class RegionAndPositionTests {

	@Autowired
	lateinit var regionService: RegionService

	@Autowired
	lateinit var employeePositionService: EmployeePositionService

	@Test
	fun testSeededRegionsExist() {
		val regions = regionService.listAll()
		assertTrue(regions.isNotEmpty(), "Default regions should be seeded on startup")
		val hasTashkent = regions.any { it.name == "Toshkent shahri" }
		assertTrue(hasTashkent, "Toshkent shahri should be one of the default seeded regions")
	}

	@Test
	fun testCreateRegion() {
		val newRegion = regionService.create(CreateRegionRequest(name = "New Test Region", code = "NTR"))
		assertNotNull(newRegion.id)
		assertEquals("New Test Region", newRegion.name)
		assertEquals("NTR", newRegion.code)

		val regions = regionService.listAll()
		assertTrue(regions.any { it.name == "New Test Region" })
	}

	@Test
	fun testSeededPositionsExist() {
		val positions = employeePositionService.listAll()
		assertTrue(positions.isNotEmpty(), "Default positions should be seeded on startup")
		val hasInspector = positions.any { it.name == "Inspektor" }
		assertTrue(hasInspector, "Inspektor should be one of the default seeded positions")
	}

	@Test
	fun testCreatePosition() {
		val newPosition = employeePositionService.create(CreateEmployeePositionRequest(name = "New Test Position", description = "Desc"))
		assertNotNull(newPosition.id)
		assertEquals("New Test Position", newPosition.name)
		assertEquals("Desc", newPosition.description)

		val positions = employeePositionService.listAll()
		assertTrue(positions.any { it.name == "New Test Position" })
	}
}

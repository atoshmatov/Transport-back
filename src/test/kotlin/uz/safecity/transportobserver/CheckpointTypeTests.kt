package uz.safecity.transportobserver

import uz.safecity.transportobserver.checkpoints.dto.CreateCheckpointRequest
import uz.safecity.transportobserver.checkpoints.service.CheckpointService
import uz.safecity.transportobserver.checkpointtypes.dto.CreateCheckpointTypeRequest
import uz.safecity.transportobserver.checkpointtypes.dto.UpdateCheckpointTypeRequest
import uz.safecity.transportobserver.checkpointtypes.service.CheckpointTypeService
import uz.safecity.transportobserver.common.exception.ConflictException
import uz.safecity.transportobserver.common.exception.ResourceNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class CheckpointTypeTests {

	@Autowired
	lateinit var checkpointTypeService: CheckpointTypeService

	@Autowired
	lateinit var checkpointService: CheckpointService

	@Test
	fun testSeededCheckpointTypesExist() {
		val types = checkpointTypeService.listAll()
		assertTrue(types.isNotEmpty(), "Default checkpoint types should be seeded on startup")
		listOf("Avtovokzal", "Temiryo'l vokzali", "Magistral yo'l", "Aeroport").forEach { expected ->
			assertTrue(types.any { it.name == expected }, "$expected should be one of the default seeded checkpoint types")
		}
	}

	@Test
	fun testCreateCheckpointType() {
		val created = checkpointTypeService.create(CreateCheckpointTypeRequest(name = "Yangi test turi", description = "Desc"))
		assertNotNull(created.id)
		assertEquals("Yangi test turi", created.name)
		assertEquals("Desc", created.description)
		assertTrue(checkpointTypeService.listAll().any { it.id == created.id })
	}

	@Test
	fun testCreateCheckpointTypeDuplicateNameConflicts() {
		checkpointTypeService.create(CreateCheckpointTypeRequest(name = "Duplikat turi"))
		assertThrows(ConflictException::class.java) {
			checkpointTypeService.create(CreateCheckpointTypeRequest(name = "Duplikat turi"))
		}
	}

	@Test
	fun testUpdateCheckpointType() {
		val created = checkpointTypeService.create(CreateCheckpointTypeRequest(name = "Eski nom"))
		val updated = checkpointTypeService.update(created.id, UpdateCheckpointTypeRequest(name = "Yangi nom", description = "Yangilangan"))
		assertEquals("Yangi nom", updated.name)
		assertEquals("Yangilangan", updated.description)
	}

	@Test
	fun testDeleteCheckpointType() {
		val created = checkpointTypeService.create(CreateCheckpointTypeRequest(name = "O'chiriladigan turi"))
		checkpointTypeService.delete(created.id)
		assertThrows(ResourceNotFoundException::class.java) { checkpointTypeService.getById(created.id) }
	}

	@Test
	fun testGetByIdNotFoundThrows() {
		assertThrows(ResourceNotFoundException::class.java) { checkpointTypeService.getById(UUID.randomUUID()) }
	}

	@Test
	fun testCreateCheckpointWithCheckpointTypeIdResolvesTypeName() {
		val type = checkpointTypeService.create(CreateCheckpointTypeRequest(name = "Test uchun Avtovokzal turi"))

		val checkpoint = checkpointService.create(
			CreateCheckpointRequest(
				name = "Test nazorat punkti",
				latitude = 41.311081,
				longitude = 69.240562,
				checkpointTypeId = type.id
			)
		)

		assertEquals(type.id, checkpoint.checkpointTypeId)
		assertEquals(type.name, checkpoint.checkpointTypeName)

		val fetched = checkpointService.getById(checkpoint.id)
		assertEquals(type.id, fetched.checkpointTypeId)
		assertEquals(type.name, fetched.checkpointTypeName)

		val listed = checkpointService.list(null, null, null, type.id, PageRequest.of(0, 20))
		assertTrue(listed.content.any { it.id == checkpoint.id })
	}

	@Test
	fun testCreateCheckpointWithUnknownCheckpointTypeIdThrows() {
		assertThrows(ResourceNotFoundException::class.java) {
			checkpointService.create(
				CreateCheckpointRequest(
					name = "Noto'g'ri turdagi nazorat punkti",
					latitude = 41.0,
					longitude = 69.0,
					checkpointTypeId = UUID.randomUUID()
				)
			)
		}
	}

	@Test
	fun testCreateCheckpointWithoutCheckpointTypeIdStaysNull() {
		val checkpoint = checkpointService.create(
			CreateCheckpointRequest(
				name = "Turi ko'rsatilmagan nazorat punkti",
				latitude = 40.0,
				longitude = 68.0
			)
		)
		assertNull(checkpoint.checkpointTypeId)
		assertNull(checkpoint.checkpointTypeName)
	}
}

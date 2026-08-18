package uz.safecity.transportobserver.common.exception

import uz.safecity.transportobserver.regions.service.RegionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Regression coverage for the centralized error-message refactor (messages.properties +
 * [Messages] + key-based [ApiException] constructors): confirms a real Spring context wires
 * [Messages] to the [org.springframework.context.MessageSource] bean and resolves both a
 * no-args key (e.g. [ConflictException]'s default) and a `{0}`-placeholder key (with an
 * apostrophe-containing literal segment, exercised via [RegionService.getById] -> "error.region.not-found")
 * to the exact same Uzbek text the old hardcoded string literals produced.
 */
@SpringBootTest
@Transactional
class MessagesResolutionTests {

	@Autowired
	lateinit var regionService: RegionService

	@Test
	fun `no-arg key resolves to full Uzbek text`() {
		assertEquals("Bu amalni bajarishga ruxsatingiz yo'q", Messages.resolve("error.common.forbidden"))
	}

	@Test
	fun `placeholder key resolves with the argument substituted`() {
		val id = UUID.randomUUID()
		assertEquals("Hudud topilmadi: $id", Messages.resolve("error.region.not-found", id))
	}

	@Test
	fun `unknown key falls back to the raw key instead of throwing`() {
		assertEquals("error.does-not-exist", Messages.resolve("error.does-not-exist"))
	}

	@Test
	fun `ResourceNotFoundException thrown from a real service resolves through MessageSource end-to-end`() {
		val id = UUID.randomUUID()
		val ex = assertThrows(ResourceNotFoundException::class.java) { regionService.getById(id) }
		assertEquals("Hudud topilmadi: $id", ex.message)
	}

	@Test
	fun `apostrophe-containing placeholder message is not mangled by MessageFormat quoting`() {
		val id = UUID.randomUUID()
		assertEquals("Xodimga bog'langan hisob topilmadi: $id", Messages.resolve("error.employee.account-not-found", id))
	}
}

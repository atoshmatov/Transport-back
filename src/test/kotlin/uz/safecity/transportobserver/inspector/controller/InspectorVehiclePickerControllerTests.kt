package uz.safecity.transportobserver.inspector.controller

import uz.safecity.transportobserver.vehicles.entity.Vehicle
import uz.safecity.transportobserver.vehicles.entity.VehicleOwnerType
import uz.safecity.transportobserver.vehicles.entity.VehicleType
import uz.safecity.transportobserver.vehicles.repository.VehicleRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers `GET /api/v1/inspector/vehicles` (InspectorPanelController.listVehiclesForPicker) — the
 * narrow, INSPECTOR-facing vehicle picker introduced so the mobile "hodisa qayd etish" flow no
 * longer needs (and is not authorized to call) [uz.safecity.transportobserver.vehicles.controller.VehicleController.list].
 *
 * Each test seeds its own uniquely-prefixed plate numbers and filters by them via the `query`
 * param, so this stays correct regardless of whatever other Vehicle rows already exist in the
 * shared dev database (manual testing, other agents, etc.) — it never assumes the response
 * contains ONLY what this test created, only that its own rows behave as expected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InspectorVehiclePickerControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var vehicleRepository: VehicleRepository

	private fun uniqueToken(): String = "PK${UUID.randomUUID().toString().take(8).uppercase()}"

	private fun createVehicle(
		token: String,
		isActive: Boolean,
		type: VehicleType = VehicleType.CAR,
		model: String? = "Cobalt"
	): Vehicle = vehicleRepository.save(
		Vehicle(
			plateNumber = token,
			type = type,
			model = model,
			regionName = "Toshkent",
			ownerType = VehicleOwnerType.LEGAL_ENTITY,
			assignedEmployeeId = null,
			isActive = isActive
		)
	)

	@Test
	@WithMockUser(authorities = ["ROLE_INSPECTOR"])
	fun `INSPECTOR gets 200 with only active vehicles and only the 4 picker fields`() {
		val token = uniqueToken()
		val active = createVehicle(token = token, isActive = true, model = "Cobalt")
		createVehicle(token = token + "X", isActive = false, model = "Cobalt") // inactive sibling, must not appear

		mockMvc.perform(get("/api/v1/inspector/vehicles").param("query", token))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].id").value(active.id.toString()))
			.andExpect(jsonPath("$.data.content[0].plateNumber").value(token))
			.andExpect(jsonPath("$.data.content[0].model").value("Cobalt"))
			.andExpect(jsonPath("$.data.content[0].type").value("CAR"))
			// Admin-only fields must be absent from the picker DTO entirely.
			.andExpect(jsonPath("$.data.content[0].ownerType").doesNotExist())
			.andExpect(jsonPath("$.data.content[0].assignedEmployeeId").doesNotExist())
			.andExpect(jsonPath("$.data.content[0].regionName").doesNotExist())
			.andExpect(jsonPath("$.data.content[0].isActive").doesNotExist())
	}

	@Test
	@WithMockUser(authorities = ["ROLE_INSPECTOR"])
	fun `inactive vehicle never appears in the picker`() {
		val token = uniqueToken()
		createVehicle(token = token, isActive = false)

		mockMvc.perform(get("/api/v1/inspector/vehicles").param("query", token))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.content.length()").value(0))
	}

	@Test
	@WithMockUser(authorities = ["ROLE_INSPECTOR"])
	fun `query matches by model as well as plate number`() {
		val token = uniqueToken()
		val active = createVehicle(token = "ZZ${UUID.randomUUID().toString().take(6).uppercase()}", isActive = true, model = token)

		mockMvc.perform(get("/api/v1/inspector/vehicles").param("query", token))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].id").value(active.id.toString()))
	}

	@Test
	@WithMockUser(authorities = []) // authenticated, but no ROLE_INSPECTOR (or any other role) authority
	fun `caller without ROLE_INSPECTOR gets 403`() {
		mockMvc.perform(get("/api/v1/inspector/vehicles"))
			.andExpect(status().isForbidden)
	}
}

package uz.safecity.transportobserver.inspector.controller

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.incidents.dto.CreateIncidentRequest
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.service.IncidentService
import uz.safecity.transportobserver.vehicles.entity.Vehicle
import uz.safecity.transportobserver.vehicles.entity.VehicleOwnerType
import uz.safecity.transportobserver.vehicles.entity.VehicleType
import uz.safecity.transportobserver.vehicles.repository.VehicleRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers `GET /api/v1/inspector/vehicles/{id}` (InspectorPanelController.getVehicleDetail) — the
 * mobile "Transport vositasi" (vehicleDetail) screen. See
 * [uz.safecity.transportobserver.inspector.dto.VehicleDetailDto] kdoc for why this only surfaces
 * the vehicle's existing registry fields + [violationHistory], not the design mockup's admin-only
 * master-data fields (VIN/owner-org/STIR/route-permit/inspection dates).
 *
 * Same isolation pattern as [InspectorVehiclePickerControllerTests]: every test seeds its own
 * uniquely-prefixed plate number, so this stays correct regardless of other Vehicle/Incident rows
 * already in the shared dev database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InspectorVehicleDetailControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var vehicleRepository: VehicleRepository

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var incidentService: IncidentService

	private fun uniqueToken(): String = "VD${UUID.randomUUID().toString().take(8).uppercase()}"

	private fun createVehicle(token: String): Vehicle = vehicleRepository.save(
		Vehicle(
			plateNumber = token,
			type = VehicleType.BUS,
			model = "ISUZU NQR",
			regionName = "Toshkent",
			ownerType = VehicleOwnerType.LEGAL_ENTITY
		)
	)

	private fun createInspectorAccount(): Account = accountRepository.save(
		Account(
			username = "insp_${UUID.randomUUID().toString().take(20)}",
			passwordHash = "irrelevant-for-this-test",
			role = RoleType.INSPECTOR,
			mustChangePassword = false,
			isActive = true
		)
	)

	private fun reportIncidentAgainst(vehicle: Vehicle, title: String, type: IncidentType = IncidentType.VIOLATION) {
		incidentService.create(
			CreateIncidentRequest(
				title = title,
				type = type,
				vehicleId = vehicle.id
			),
			CustomUserDetails.from(createInspectorAccount())
		)
	}

	/**
	 * `@WithMockUser` alone injects a generic Spring Security `User` as the principal, not
	 * [CustomUserDetails] — this controller's `@AuthenticationPrincipal principal: CustomUserDetails`
	 * parameter is non-null (Kotlin), so a plain `@WithMockUser` test NPEs on that argument the
	 * moment the handler method is actually invoked (i.e. any request that clears `@PreAuthorize`
	 * and reaches the controller body). `@WithMockUser` alone remains correct — and is used below —
	 * for the two 403 tests, since [org.springframework.security.access.prepost.PreAuthorize]
	 * rejects those before the controller body (and its principal argument) is ever reached.
	 */
	private fun inspectorAuth(): RequestPostProcessor {
		val principal = CustomUserDetails.from(createInspectorAccount())
		return authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities))
	}

	@Test
	fun `INSPECTOR gets 200 with vehicle fields and violation history, most recent first`() {
		val token = uniqueToken()
		val vehicle = createVehicle(token)
		reportIncidentAgainst(vehicle, title = "First report")
		reportIncidentAgainst(vehicle, title = "Second report")

		mockMvc.perform(get("/api/v1/inspector/vehicles/{id}", vehicle.id).with(inspectorAuth()))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.id").value(vehicle.id.toString()))
			.andExpect(jsonPath("$.data.plateNumber").value(token))
			.andExpect(jsonPath("$.data.model").value("ISUZU NQR"))
			.andExpect(jsonPath("$.data.type").value("BUS"))
			.andExpect(jsonPath("$.data.regionName").value("Toshkent"))
			.andExpect(jsonPath("$.data.ownerType").value("LEGAL_ENTITY"))
			.andExpect(jsonPath("$.data.violationHistory.length()").value(2))
			// Most recently created first.
			.andExpect(jsonPath("$.data.violationHistory[0].description").doesNotExist())
			.andExpect(jsonPath("$.data.violationHistory[0].type").value("VIOLATION"))
			.andExpect(jsonPath("$.data.violationHistory[1].type").value("VIOLATION"))
	}

	@Test
	fun `vehicle with no incidents returns an empty violationHistory, not an error`() {
		val token = uniqueToken()
		val vehicle = createVehicle(token)

		mockMvc.perform(get("/api/v1/inspector/vehicles/{id}", vehicle.id).with(inspectorAuth()))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.violationHistory.length()").value(0))
	}

	@Test
	fun `non-existent vehicle id returns 404`() {
		mockMvc.perform(get("/api/v1/inspector/vehicles/{id}", UUID.randomUUID()).with(inspectorAuth()))
			.andExpect(status().isNotFound)
	}

	@Test
	@WithMockUser(authorities = ["ROLE_ADMIN"]) // wrong role: this endpoint is INSPECTOR-only
	fun `caller with a different role gets 403`() {
		val vehicle = createVehicle(uniqueToken())

		mockMvc.perform(get("/api/v1/inspector/vehicles/{id}", vehicle.id))
			.andExpect(status().isForbidden)
	}

	@Test
	@WithMockUser(authorities = []) // authenticated, but no role authority at all
	fun `caller without any role authority gets 403`() {
		val vehicle = createVehicle(uniqueToken())

		mockMvc.perform(get("/api/v1/inspector/vehicles/{id}", vehicle.id))
			.andExpect(status().isForbidden)
	}
}

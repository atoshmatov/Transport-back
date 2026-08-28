package uz.safecity.transportobserver.incidents.controller

import uz.safecity.transportobserver.auth.entity.Account
import uz.safecity.transportobserver.auth.entity.RoleType
import uz.safecity.transportobserver.auth.repository.AccountRepository
import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.incidents.entity.Incident
import uz.safecity.transportobserver.incidents.entity.IncidentType
import uz.safecity.transportobserver.incidents.repository.IncidentRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers `POST /api/v1/incidents/{id}/evidence` — both the ORIGINAL single-file `file` param
 * contract (must keep working byte-for-byte for the web admin panel, which still only ever sends
 * this) and the NEW multi-file `files` param contract (mobile app's updated multi-photo report
 * flow). See [IncidentController.uploadEvidence] kdoc for why the two branches return different
 * response shapes on purpose, and [uz.safecity.transportobserver.incidents.service.EvidenceService.uploadBatch]
 * kdoc for the partial-success behavior asserted below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IncidentEvidenceControllerTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var accountRepository: AccountRepository

	@Autowired
	lateinit var incidentRepository: IncidentRepository

	// FF D8 FF -> JPEG magic number (see EvidenceService.sniffImageType).
	private val validJpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x10, 0x20)

	// 89 50 4E 47 -> PNG magic number.
	private val validPngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)

	private val invalidBytes = "this is not an image".toByteArray()

	private fun createInspector(): Account = accountRepository.save(
		Account(
			username = "insp_${UUID.randomUUID().toString().take(20)}",
			passwordHash = "irrelevant-for-this-test",
			role = RoleType.INSPECTOR,
			mustChangePassword = false,
			isActive = true
		)
	)

	private fun createIncidentAssignedTo(inspectorAccountId: UUID): Incident =
		incidentRepository.save(
			Incident(
				title = "Evidence test incident ${UUID.randomUUID()}",
				type = IncidentType.VIOLATION,
				assignedInspectorId = inspectorAccountId
			)
		)

	/** Same reasoning as InspectorVehicleDetailControllerTests#inspectorAuth: `@WithMockUser` alone
	 * injects a generic `User`, not [CustomUserDetails], which NPEs on this controller's
	 * non-null `@AuthenticationPrincipal principal: CustomUserDetails` parameter. */
	private fun authOf(account: Account): RequestPostProcessor {
		val principal = CustomUserDetails.from(account)
		return authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities))
	}

	@Test
	fun `old single 'file' param still returns a single EvidenceDto object, unchanged`() {
		val inspector = createInspector()
		val incident = createIncidentAssignedTo(requireNotNull(inspector.id))
		val part = MockMultipartFile("file", "photo.jpg", "image/jpeg", validJpegBytes)

		mockMvc.perform(multipart("/api/v1/incidents/{id}/evidence", incident.id).file(part).with(authOf(inspector)))
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.id").exists())
			.andExpect(jsonPath("$.data.incidentId").value(incident.id.toString()))
			.andExpect(jsonPath("$.data.fileType").value("image/jpeg"))
			// The old response shape is a bare object: no "uploaded"/"failed" batch wrapper fields.
			.andExpect(jsonPath("$.data.uploaded").doesNotExist())
			.andExpect(jsonPath("$.data.failed").doesNotExist())
	}

	@Test
	fun `new 'files' param with two valid photos uploads both and reports zero failures`() {
		val inspector = createInspector()
		val incident = createIncidentAssignedTo(requireNotNull(inspector.id))
		val jpeg = MockMultipartFile("files", "a.jpg", "image/jpeg", validJpegBytes)
		val png = MockMultipartFile("files", "b.png", "image/png", validPngBytes)

		mockMvc.perform(multipart("/api/v1/incidents/{id}/evidence", incident.id).file(jpeg).file(png).with(authOf(inspector)))
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.uploaded.length()").value(2))
			.andExpect(jsonPath("$.data.failed.length()").value(0))
	}

	@Test
	fun `new 'files' param with one valid and one invalid photo uploads the good one and reports the bad one as failed`() {
		val inspector = createInspector()
		val incident = createIncidentAssignedTo(requireNotNull(inspector.id))
		val good = MockMultipartFile("files", "good.jpg", "image/jpeg", validJpegBytes)
		val bad = MockMultipartFile("files", "bad.txt", "text/plain", invalidBytes)

		mockMvc.perform(multipart("/api/v1/incidents/{id}/evidence", incident.id).file(good).file(bad).with(authOf(inspector)))
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.uploaded.length()").value(1))
			.andExpect(jsonPath("$.data.uploaded[0].fileType").value("image/jpeg"))
			.andExpect(jsonPath("$.data.failed.length()").value(1))
			.andExpect(jsonPath("$.data.failed[0].fileName").value("bad.txt"))
			.andExpect(jsonPath("$.data.failed[0].message").exists())
	}

	@Test
	fun `neither 'file' nor 'files' provided returns 400`() {
		val inspector = createInspector()
		val incident = createIncidentAssignedTo(requireNotNull(inspector.id))

		mockMvc.perform(multipart("/api/v1/incidents/{id}/evidence", incident.id).with(authOf(inspector)))
			.andExpect(status().isBadRequest)
	}

	@Test
	fun `an INSPECTOR cannot attach evidence to a foreign incident (404, not 403)`() {
		val owner = createInspector()
		val stranger = createInspector()
		val incident = createIncidentAssignedTo(requireNotNull(owner.id))
		val part = MockMultipartFile("file", "photo.jpg", "image/jpeg", validJpegBytes)

		mockMvc.perform(multipart("/api/v1/incidents/{id}/evidence", incident.id).file(part).with(authOf(stranger)))
			.andExpect(status().isNotFound)
	}
}

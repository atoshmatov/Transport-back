package uz.safecity.transportobserver.notifications.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.notifications.entity.Notification
import uz.safecity.transportobserver.notifications.service.NotificationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
	private val notificationService: NotificationService
) {

	@GetMapping
	fun listMine(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<ApiResponse<List<Notification>>> =
		ResponseEntity.ok(ApiResponse.ok(notificationService.listForAccount(principal.accountId)))
}

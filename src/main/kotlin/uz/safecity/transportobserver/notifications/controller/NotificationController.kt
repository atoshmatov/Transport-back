package uz.safecity.transportobserver.notifications.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.notifications.dto.MarkAllReadResponseDto
import uz.safecity.transportobserver.notifications.dto.UnreadCountDto
import uz.safecity.transportobserver.notifications.entity.Notification
import uz.safecity.transportobserver.notifications.service.NotificationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Every endpoint here is scoped to the caller's own notifications (`principal.accountId`) — no
 * role restriction beyond "authenticated", since every [uz.safecity.transportobserver.auth.entity.RoleType]
 * can receive notifications (see [NotificationService] kdoc for the scoping pattern).
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
	private val notificationService: NotificationService
) {

	@GetMapping
	fun listMine(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<ApiResponse<List<Notification>>> =
		ResponseEntity.ok(ApiResponse.ok(notificationService.listForAccount(principal.accountId)))

	/** Badge count for the bell icon — unread notifications belonging to the caller only. */
	@GetMapping("/unread-count")
	fun unreadCount(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<ApiResponse<UnreadCountDto>> =
		ResponseEntity.ok(ApiResponse.ok(UnreadCountDto(notificationService.unreadCountForAccount(principal.accountId))))

	/** Marks a single notification read. 404s on a foreign notification id — see [NotificationService] kdoc. */
	@PatchMapping("/{id}/read")
	fun markAsRead(
		@PathVariable id: UUID,
		@AuthenticationPrincipal principal: CustomUserDetails
	): ResponseEntity<ApiResponse<Notification>> =
		ResponseEntity.ok(ApiResponse.ok(notificationService.markAsRead(id, principal.accountId)))

	/** Marks every unread notification of the caller as read in one shot. */
	@PatchMapping("/read-all")
	fun markAllAsRead(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<ApiResponse<MarkAllReadResponseDto>> =
		ResponseEntity.ok(ApiResponse.ok(MarkAllReadResponseDto(notificationService.markAllAsRead(principal.accountId))))
}

package uz.safecity.transportobserver.notifications.dto

/** `GET /api/v1/notifications/unread-count` response — badge count for the caller. */
data class UnreadCountDto(val count: Long)

/** `PATCH /api/v1/notifications/read-all` response — how many rows were actually flipped. */
data class MarkAllReadResponseDto(val updatedCount: Int)

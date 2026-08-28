package uz.safecity.transportobserver.auth.security

/**
 * Best-effort platform/device labels for the "Faol sessiyalar" (Active Sessions) list shown in
 * the admin panel's Profile > Faoliyat tab — see [RefreshTokenService] kdoc for the Redis session
 * metadata this feeds, and [uz.safecity.transportobserver.auth.dto.SessionDto] for the wire shape.
 *
 * [ClientPlatform.HEADER_NAME] only ever carries `"mobile"` (see its kdoc — the native app shares
 * one Ktor `HttpClient` for both Android and iOS builds, and the header's only existing job is the
 * cookie-jar workaround), so it cannot by itself distinguish an Android build from an iOS one. The
 * `User-Agent` string CAN, in practice, because each platform's default HTTP engine stamps its own
 * OS fingerprint into it. This is purely a display label for a human reading a list in the admin
 * panel — nothing here feeds an authn/authz decision, so a spoofed/missing User-Agent only ever
 * degrades to a generic "MOBILE"/null label, never a security gap.
 */
object SessionDevice {

	private const val MAX_DEVICE_LABEL_LENGTH = 120

	/** One of `"WEB"`, `"ANDROID"`, `"IOS"`, or `"MOBILE"` (mobile header set, OS undetectable from UA). */
	fun resolvePlatform(clientPlatformHeader: String?, userAgent: String?): String {
		if (!ClientPlatform.isMobile(clientPlatformHeader)) return "WEB"
		val ua = userAgent.orEmpty()
		return when {
			ua.contains("Android", ignoreCase = true) -> "ANDROID"
			ua.contains("iPhone", ignoreCase = true) ||
				ua.contains("iPad", ignoreCase = true) ||
				ua.contains("Darwin", ignoreCase = true) ||
				ua.contains("CFNetwork", ignoreCase = true) -> "IOS"
			else -> "MOBILE"
		}
	}

	/** Short "Browser · OS" style summary of [userAgent], or `null` when it's blank/absent. */
	fun summarize(userAgent: String?): String? {
		val ua = userAgent?.trim()?.takeIf { it.isNotEmpty() } ?: return null

		val os = when {
			ua.contains("Windows", ignoreCase = true) -> "Windows"
			ua.contains("Android", ignoreCase = true) -> "Android"
			ua.contains("iPhone", ignoreCase = true) -> "iPhone"
			ua.contains("iPad", ignoreCase = true) -> "iPad"
			ua.contains("Mac OS X", ignoreCase = true) || ua.contains("Macintosh", ignoreCase = true) -> "macOS"
			ua.contains("Linux", ignoreCase = true) -> "Linux"
			else -> null
		}

		// Order matters: Edge/Opera/Chrome-on-iOS all also match "Safari"/"Chrome" tokens in their
		// own UA strings, so the more specific tokens must be checked first.
		val browser = when {
			ua.contains("Edg/", ignoreCase = true) -> "Edge"
			ua.contains("OPR/", ignoreCase = true) || ua.contains("Opera", ignoreCase = true) -> "Opera"
			ua.contains("CriOS/", ignoreCase = true) -> "Chrome"
			ua.contains("Chrome/", ignoreCase = true) -> "Chrome"
			ua.contains("Firefox/", ignoreCase = true) -> "Firefox"
			ua.contains("Safari/", ignoreCase = true) -> "Safari"
			else -> null
		}

		val label = listOfNotNull(browser, os).joinToString(" · ").ifBlank { ua }
		return label.take(MAX_DEVICE_LABEL_LENGTH)
	}
}

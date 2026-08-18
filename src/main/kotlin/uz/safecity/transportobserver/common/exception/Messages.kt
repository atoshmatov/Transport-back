package uz.safecity.transportobserver.common.exception

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component

/**
 * Static bridge from exception constructors (`throw ResourceNotFoundException("error.x", id)`)
 * to Spring's [MessageSource] + `src/main/resources/messages.properties` (see that file's header
 * for the full key/placeholder convention). Exceptions are plain `throw`n objects, not Spring
 * beans, so they can't get a [MessageSource] constructor-injected the normal way — [MessagesBinder]
 * hands this object the app's singleton [MessageSource] once, right after Spring builds it, and
 * every exception constructor reads it from here via [resolve].
 *
 * Locale comes from [LocaleContextHolder], which Spring MVC's `DispatcherServlet` populates from
 * the request's `Accept-Language` header (via the default `AcceptHeaderLocaleResolver`) for the
 * duration of each request — so adding `messages_ru.properties` / `messages_en.properties` later
 * with the same keys is enough to make every [uz.safecity.transportobserver.common.exception.ApiException]
 * respond in that language; no code changes needed here.
 *
 * [resolve] never throws: an unresolvable/mistyped key falls back to the raw key text (plus its
 * args) instead of turning a routine 404/400 into a 500 just because a properties key was typo'd,
 * and also covers the case where an exception is constructed before the Spring context has
 * finished starting (e.g. directly in a plain unit test with no context at all).
 */
object Messages {

	@Volatile
	private var messageSource: MessageSource? = null

	internal fun bind(source: MessageSource) {
		messageSource = source
	}

	fun resolve(key: String, vararg args: Any?): String {
		val source = messageSource
			?: return if (args.isEmpty()) key else "$key ${args.joinToString(", ")}"
		return source.getMessage(key, args, key, LocaleContextHolder.getLocale()) ?: key
	}
}

/** See [Messages] kdoc — this is just the one-time wiring step. */
@Component
internal class MessagesBinder(source: MessageSource) {
	init {
		Messages.bind(source)
	}
}

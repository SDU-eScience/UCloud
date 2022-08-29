package dk.sdu.cloud.calls

import kotlinx.serialization.KSerializer
import kotlin.reflect.KProperty1

class HttpRequest<R : Any, S : Any, E : Any>(
    val context: CallDescription<R, S, E>,
    val path: HttpPath<R>,
    val body: HttpBody<R, *>?,
    val params: HttpParams<R>?,
    val method: HttpMethod,
    val headers: HttpHeaderDescription<R>?
) {
    init {
        var bindingFrom = 0
        if (body != null) bindingFrom++
        if (params != null) bindingFrom++
        if (headers != null) bindingFrom++
        if (bindingFrom > 1) {
            throw IllegalArgumentException("UCloud no longer supports binding from multiple sources " +
                "(e.g. body, params, headers)")
        }
    }

    companion object {
        internal val callKey = AttributeKey<HttpRequest<*, *, *>>("http-request")
    }
}

sealed class HttpBody<Request : Any, Property> {
    abstract val ref: KSerializer<Property>

    data class BoundToEntireRequest<Request : Any>(
        override val ref: KSerializer<Request>,
    ) : HttpBody<Request, Request>()
}

data class HttpPath<R : Any>(
    val basePath: String,
    val segments: List<HttpPathSegment<R>>
)

sealed class HttpPathSegment<Request : Any> {
    data class Simple<Request : Any>(
        val text: String
    ) : HttpPathSegment<Request>()
}

fun HttpPath<*>.toPath(): String {
    val primaryPart = segments.joinToString("/") { it.toPath() }
    return (basePath.removeSuffix("/") + "/" + primaryPart).removeSuffix("/")
}

private fun <R : Any> HttpPathSegment<R>.toPath(): String = when (this) {
    is HttpPathSegment.Simple -> text
}

data class HttpParams<Request : Any>(val parameters: List<HttpQueryParameter<Request>>)

sealed class HttpQueryParameter<Request : Any> {
    data class Property<Request : Any>(
        val property: String,
        val nestedInside: String? = null
    ) : HttpQueryParameter<Request>()
}

data class HttpHeaderDescription<Request : Any>(val parameters: List<HttpHeaderParameter<Request>>)

sealed class HttpHeaderParameter<Request : Any> {
    data class Simple<Request : Any>(val header: String, val value: String) : HttpHeaderParameter<Request>()

    data class Present<Request : Any>(val header: String) : HttpHeaderParameter<Request>()

    data class Property<Request : Any, Property>(
        val header: String,
        val property: KProperty1<Request, Property>,
        val base64Encoded: Boolean
    ) : HttpHeaderParameter<Request>()
}

@Suppress("UNCHECKED_CAST")
val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.http: HttpRequest<R, S, E>
    get() = attributes[HttpRequest.callKey] as HttpRequest<R, S, E>

@Suppress("UNCHECKED_CAST")
val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.httpOrNull: HttpRequest<R, S, E>?
    get() = attributes.getOrNull(HttpRequest.callKey) as HttpRequest<R, S, E>?

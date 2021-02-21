package dk.sdu.cloud.calls

import com.fasterxml.jackson.core.type.TypeReference
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import kotlin.reflect.KProperty1

class HttpRequest<R : Any, S : Any, E : Any>(
    val context: CallDescription<R, S, E>,
    val path: HttpPath<R>,
    val body: HttpBody<R, *>?,
    val params: HttpParams<R>?,
    val method: HttpMethod,
    val headers: HttpHeaderDescription<R>?
) {
    companion object {
        internal val callKey = AttributeKey<HttpRequest<*, *, *>>("http-request")
    }
}

sealed class HttpBody<Request : Any, Property> {
    abstract val ref: TypeReference<Property>

    data class BoundToSubProperty<Request : Any, Property>(
        val property: KProperty1<Request, *>,
        override val ref: TypeReference<Property>,
        val outgoingConverter: ((Property) -> OutgoingContent)?,
        val ingoingConverter: (suspend (ApplicationCall) -> Property)?
    ) : HttpBody<Request, Property>()

    data class BoundToEntireRequest<Request : Any>(
        override val ref: TypeReference<Request>,
        val outgoingConverter: ((Request) -> OutgoingContent)?,
        val ingoingConverter: (suspend (ApplicationCall) -> Request)?
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

    data class Property<Request : Any, Property>(
        val property: KProperty1<Request, Property>,
        val outgoingConverter: ((Property) -> String)?,
        val ingoingConverter: ((String) -> Property)?
    ) : HttpPathSegment<Request>()

    class Remaining<Request : Any> : HttpPathSegment<Request>()
}

fun HttpPath<*>.toKtorTemplate(fullyQualified: Boolean = false): String {
    val primaryPart = segments.joinToString("/") { it.toKtorTemplateString() }
    return if (fullyQualified) {
        basePath.removeSuffix("/") + "/" + primaryPart
    } else {
        primaryPart
    }
}

private fun <R : Any> HttpPathSegment<R>.toKtorTemplateString(): String = when (this) {
    is HttpPathSegment.Simple -> text

    is HttpPathSegment.Property<R, *> -> StringBuilder().apply {
        append('{')
        append(property.name)
        if (property.returnType.isMarkedNullable) append('?')
        append('}')
    }.toString()

    is HttpPathSegment.Remaining -> "{...}"
}

data class HttpParams<Request : Any>(val parameters: List<HttpQueryParameter<Request>>)

sealed class HttpQueryParameter<Request : Any> {
    data class Property<Request : Any, Property>(
        val property: KProperty1<Request, Property>,
        val outgoingConverter: ((Property) -> String)?,
        val ingoingConverter: ((name: String, value: String) -> Property)?
    ) : HttpQueryParameter<Request>()
}

data class HttpHeaderDescription<Request : Any>(val parameters: List<HttpHeaderParameter<Request>>)

sealed class HttpHeaderParameter<Request : Any> {
    data class Simple<Request : Any>(val header: String, val value: String) : HttpHeaderParameter<Request>()

    data class Present<Request : Any>(val header: String) : HttpHeaderParameter<Request>()

    data class Property<Request : Any, Property>(
        val header: String,
        val property: KProperty1<Request, Property>,
        val outgoingConverter: ((Property) -> String)?,
        val ingoingConverter: ((value: String) -> Property)?
    ) : HttpHeaderParameter<Request>()
}

@Suppress("UNCHECKED_CAST")
val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.http: HttpRequest<R, S, E>
    get() = attributes[HttpRequest.callKey] as HttpRequest<R, S, E>

@Suppress("UNCHECKED_CAST")
val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.httpOrNull: HttpRequest<R, S, E>?
    get() = attributes.getOrNull(HttpRequest.callKey) as HttpRequest<R, S, E>?

package dk.sdu.cloud.calls

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import kotlin.reflect.KProperty1

class HttpDSLException(why: String) : RuntimeException(why)

class HttpRequestBuilder<R : Any, S : Any, E : Any> internal constructor(private val context: CallDescription<R, S, E>) {
    var method: HttpMethod = HttpMethod.Get
    internal var body: HttpBody<R, *>? = null
    internal var path: HttpPath<R>? = null
    private var params: HttpParams<R>? = null
    private var headers: HttpHeaderDescription<R>? = null

    fun path(body: HttpCallPathBuilder<R>.() -> Unit) {
        if (path != null) throw HttpDSLException("Cannot supply two path blocks!")
        path = HttpCallPathBuilder(this).also { it.body() }.build()
    }

    fun body(builderBody: HttpCallBodyBuilder<R>.() -> Unit) {
        if (body != null) throw HttpDSLException("Cannot supply two body blocks!")
        body = HttpCallBodyBuilder(this).also(builderBody).body ?: throw HttpDSLException("Missing entries in body{}")
    }

    fun params(builderBody: HttpCallQueryParamsBuilder<R>.() -> Unit) {
        if (params != null) throw HttpDSLException("Cannot supply two param blocks!")
        params = HttpCallQueryParamsBuilder<R>().also(builderBody).build()
    }

    fun headers(builderBody: HttpCallHeadersBuilder<R>.() -> Unit) {
        if (headers != null) throw HttpDSLException("Cannot supply two header blocks!")
        headers = HttpCallHeadersBuilder<R>().also(builderBody).build()
    }

    fun build(): HttpRequest<R, S, E> {
        val path = path ?: throw HttpDSLException("Missing path { ... } block")
        return HttpRequest(context, path, body, params, method, headers)
    }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.http(handler: HttpRequestBuilder<R, S, E>.() -> Unit) {
    attributes[HttpRequest.callKey] = HttpRequestBuilder(this).also(handler).build()
}

class HttpCallPathBuilder<R : Any> internal constructor(private val parent: HttpRequestBuilder<R, *, *>) {
    private var basePath = ""
    private val segments = ArrayList<HttpPathSegment<R>>()

    private fun addSegment(segment: HttpPathSegment<R>) {
        if (segment is HttpPathSegment.Property<*, *>) {
            val isLegal = parent.body !is HttpBody.BoundToEntireRequest<*>
            if (!isLegal) {
                throw HttpDSLException(
                    """
                    Cannot bind a property to the both, the entire request has already been to the body.

                    In your code you have already bound the entire request object to the body, i.e.:

                    body {
                        bindEntireRequestFromBody()
                    }

                    If you intend to use bound properties from the path _and_ the body, then the body must be bound
                    using bindToSubProperty. For example:

                    data class Foo(val a: Int, val b: Int)
                    data class MyRequest(val pathBound: String, val bodyBound: Foo)

                    callDescription {
                        path {
                            +boundTo(MyRequest::pathBound)
                        }

                        body {
                            bindToSubProperty(MyRequest::bodyBound)
                        }
                    }
                    """.trimIndent()
                )
            }

            val alreadyExists = segments.filterIsInstance<HttpPathSegment.Property<*, *>>().find {
                it.property == segment.property
            } != null
            if (alreadyExists) {
                throw HttpDSLException(
                    """
                    Cannot bind the same property twice!

                    Somewhere in the code you have already bound this property, i.e.:

                    path {
                        +boundTo(MyRequest::${segment.property.name})
                        // ...
                        +boundTo(MyRequest::${segment.property.name}) // <-- Second call not OK
                    }
                    """.trimIndent()
                )
            }
        }

        segments.add(segment)
    }

    operator fun String.unaryPlus() {
        addSegment(HttpPathSegment.Simple(this))
    }

    operator fun HttpPathSegment<R>.unaryPlus() {
        addSegment(this)
    }

    fun using(path: String) {
        if (basePath.isNotEmpty()) throw HttpDSLException("Cannot call using(path) twice!")
        basePath = path
    }

    fun <T> boundTo(
        property: KProperty1<R, T>,
        outgoingConverter: ((T) -> String)? = null,
        ingoingConverter: ((String) -> T)? = null
    ): HttpPathSegment.Property<R, T> = HttpPathSegment.Property(property, outgoingConverter, ingoingConverter)

    fun remaining(): HttpPathSegment.Remaining<R> = HttpPathSegment.Remaining()

    internal fun build(): HttpPath<R> {
        return HttpPath(basePath, segments)
    }
}

class HttpCallQueryParamsBuilder<R : Any> internal constructor() {
    private val params = ArrayList<HttpQueryParameter<R>>()

    private fun addParam(param: HttpQueryParameter<R>) {
        params.add(param)
    }

    operator fun HttpQueryParameter<R>.unaryPlus() {
        addParam(this)
    }

    fun <T> boundTo(
        property: KProperty1<R, T>,
        outgoingConverter: ((T) -> String)? = null,
        ingoingConverter: ((name: String, value: String) -> T)? = null
    ): HttpQueryParameter.Property<R, T> {
        return HttpQueryParameter.Property(property, outgoingConverter, ingoingConverter)
    }

    fun build(): HttpParams<R> = HttpParams(params)
}

class HttpCallHeadersBuilder<R : Any> internal constructor() {
    private val parameters = ArrayList<HttpHeaderParameter<R>>()

    operator fun HttpHeaderParameter<R>.unaryPlus() {
        parameters.add(this)
    }

    operator fun Pair<String, String>.unaryPlus() {
        parameters.add(HttpHeaderParameter.Simple(first, second))
    }

    operator fun String.unaryPlus() {
        parameters.add(HttpHeaderParameter.Present(this))
    }

    fun <T> boundTo(
        header: String,
        property: KProperty1<R, T>,
        outgoingConverter: ((T) -> String)? = null,
        ingoingConverter: ((value: String) -> T)? = null
    ): HttpHeaderParameter.Property<R, T> {
        return HttpHeaderParameter.Property(header, property, outgoingConverter, ingoingConverter)
    }

    fun build(): HttpHeaderDescription<R> {
        return HttpHeaderDescription(parameters.toList())
    }
}

class HttpCallBodyBuilder<R : Any> internal constructor(private val parent: HttpRequestBuilder<R, *, *>) {
    internal var body: HttpBody<R, *>? = null

    fun bindEntireRequestFromBody(
        ref: TypeReference<R>,
        outgoingConverter: ((R) -> OutgoingContent)? = null,
        ingoingConverter: (suspend (ApplicationCall) -> R)? = null
    ) {
        checkNotBound()

        val isLegal = parent.path?.segments?.find { it is HttpPathSegment.Property<*, *> } == null
        if (!isLegal) {
            throw HttpDSLException(
                """
                Cannot bind the entire request from body, request body already contains bindings.

                You have already supplied at least one path segment which is bound to a property, e.g.:

                path {
                    +boundTo(MyRequest::pathBound)
                }

                If you intend to use bound properties from the path _and_ the body, then the body must be bound using
                bindToSubProperty. For example:

                data class Foo(val a: Int, val b: Int)
                data class MyRequest(val pathBound: String, val bodyBound: Foo)

                callDescription {
                    path {
                        +boundTo(MyRequest::pathBound)
                    }

                    body {
                        bindToSubProperty(MyRequest::bodyBound)
                    }
                }
                """.trimIndent()
            )
        }

        body = HttpBody.BoundToEntireRequest(ref, outgoingConverter, ingoingConverter)
    }

    fun <F> bindToSubProperty(
        property: KProperty1<R, F>,
        ref: TypeReference<F>,
        outgoingConverter: ((F) -> OutgoingContent)? = null,
        ingoingConverter: (suspend (ApplicationCall) -> F)? = null
    ) {
        checkNotBound()

        body = HttpBody.BoundToSubProperty(property, ref, outgoingConverter, ingoingConverter)
    }

    private fun checkNotBound() {
        if (body != null) {
            throw HttpDSLException("Cannot call body { bind*(...) } twice!")
        }
    }
}

inline fun <reified R : Any> HttpCallBodyBuilder<R>.bindEntireRequestFromBody(
    noinline outgoingConverter: ((R) -> OutgoingContent)? = null,
    noinline ingoingConverter: (suspend (ApplicationCall) -> R)? = null
) {
    bindEntireRequestFromBody(jacksonTypeRef(), outgoingConverter, ingoingConverter)
}

inline fun <R : Any, reified F> HttpCallBodyBuilder<R>.bindToSubProperty(
    property: KProperty1<R, F>,
    noinline outgoingConverter: ((F) -> OutgoingContent)? = null,
    noinline ingoingConverter: (suspend (ApplicationCall) -> F)? = null
) {
    bindToSubProperty(property, jacksonTypeRef(), outgoingConverter, ingoingConverter)
}

package dk.sdu.cloud.calls

import kotlinx.serialization.KSerializer
import kotlin.reflect.KProperty1

class HttpDSLException(why: String) : RuntimeException(why)

class HttpRequestBuilder<R : Any, S : Any, E : Any> internal constructor(
    @PublishedApi internal val context: CallDescription<R, S, E>
) {
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

    fun boundTo(property: KProperty1<*, *>): HttpQueryParameter.Property<R> {
        return HttpQueryParameter.Property(property.name)
    }

    fun boundTo(property: String, nestedInside: String? = null): HttpQueryParameter.Property<R> {
        return HttpQueryParameter.Property(property, nestedInside)
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
        base64Encoded: Boolean = true
    ): HttpHeaderParameter.Property<R, T> {
        return HttpHeaderParameter.Property(header, property, base64Encoded)
    }

    fun build(): HttpHeaderDescription<R> {
        return HttpHeaderDescription(parameters.toList())
    }
}

class HttpCallBodyBuilder<R : Any> internal constructor(@PublishedApi internal val parent: HttpRequestBuilder<R, *, *>) {
    internal var body: HttpBody<R, *>? = null

    fun bindEntireRequestFromBody(ref: KSerializer<R>) {
        checkNotBound()

        body = HttpBody.BoundToEntireRequest(ref)
    }

    private fun checkNotBound() {
        if (body != null) {
            throw HttpDSLException("Cannot call body { bind*(...) } twice!")
        }
    }
}

inline fun <reified R : Any> HttpCallBodyBuilder<R>.bindEntireRequestFromBody() {
    bindEntireRequestFromBody(parent.context.containerRef.fixedSerializer())
}

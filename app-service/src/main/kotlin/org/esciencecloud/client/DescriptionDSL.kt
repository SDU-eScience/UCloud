package org.esciencecloud.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.ktor.http.HttpMethod
import org.esciencecloud.asynchttp.HttpClient
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

@DslMarker annotation class RESTCallDSL

inline fun <reified R : Any, reified S : Any, reified E : Any> callDescription(
        mapper: ObjectMapper = HttpClient.defaultMapper,
        body: RESTCallDescriptionBuilder<R, S, E>.() -> Unit
): RESTCallDescription<R, S, E> =
        RESTCallDescriptionBuilder<R, S, E>(
                R::class,
                mapper.readerFor(jacksonTypeRef<S>()),
                mapper.readerFor(jacksonTypeRef<E>())
        ).also(body).build()

@RESTCallDSL
class RESTCallDescriptionBuilder<R : Any, S, E>(
        private val requestType: KClass<R>,
        private val deserializerSuccess: ObjectReader,
        private val deserializerError: ObjectReader
) {
    var method: HttpMethod = HttpMethod.Get
    var path: RESTPath<R>? = null

    fun path(body: RESTCallPathBuilder<R>.() -> Unit) {
        path = RESTCallPathBuilder<R>().also { it.body() }.build()
    }

    fun build(): RESTCallDescription<R, S, E> {
        val path = path ?: throw IllegalStateException("Missing path { ... }!")
        return RESTCallDescription(method, path, requestType, deserializerSuccess, deserializerError)
    }
}

@RESTCallDSL
class RESTCallPathBuilder<R : Any> {
    private var basePath = ""
    private val segments = ArrayList<RESTPathSegment<R>>()

    operator fun String.unaryPlus() {
        segments.add(RESTPathSegment.Simple(this))
    }

    operator fun RESTPathSegment<R>.unaryPlus() {
        segments.add(this)
    }

    fun using(path: String) {
        basePath = path
    }

    fun <T> boundTo(property: KProperty1<R, T>): RESTPathSegment.Property<R, T> = RESTPathSegment.Property(property)
    fun remaining(): RESTPathSegment.Remaining<R> = RESTPathSegment.Remaining()

    internal fun build(): RESTPath<R> {
        return RESTPath(basePath, segments)
    }
}
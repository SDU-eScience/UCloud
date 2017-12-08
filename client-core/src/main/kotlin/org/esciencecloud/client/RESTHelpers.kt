package org.esciencecloud.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.netty.handler.codec.http.HttpMethod
import org.asynchttpclient.BoundRequestBuilder
import org.asynchttpclient.Response
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

data class RESTCallDescription<R : Any, S, E>(
        val method: HttpMethod,
        val path: RESTPath<R>,
        val body: RESTBody<R, *>?,
        val requestType: KClass<R>,
        val shouldProxyFromGateway: Boolean,
        val deserializerSuccess: ObjectReader,
        val deserializerError: ObjectReader,
        val requestConfiguration: (BoundRequestBuilder.(R) -> Unit)? = null
) {
    fun prepare(payload: R): PreparedRESTCall<S, E> {
        val primaryPath = path.segments.mapNotNull {
            when (it) {
                is RESTPathSegment.Simple -> it.text
                is RESTPathSegment.Remaining -> ""
                is RESTPathSegment.Property<R, *> -> {
                    val value = it.property.get(payload)
                    value?.toString()
                }
            }
        }.joinToString("/")

        val resolvedPath = path.basePath.removeSuffix("/") + "/" + primaryPath
        return object : PreparedRESTCall<S, E>(resolvedPath) {
            override fun deserializeSuccess(response: Response): S {
                return deserializerSuccess.readValue(response.responseBody)
            }

            override fun deserializeError(response: Response): E? {
                return deserializerError.readValue(response.responseBody)
            }

            override fun BoundRequestBuilder.configure() {
                setMethod(method.name())
                requestConfiguration?.invoke(this, payload)
                when (body) {
                    is RESTBody.BoundToEntireRequest<*> -> {
                        setJsonBody(payload)
                    }

                    is RESTBody.BoundToSubProperty -> {
                        setJsonBody(body.property.get(payload))
                    }
                }
            }
        }
    }

    suspend fun call(payload: R, cloud: AuthenticatedCloud): RESTResponse<S, E> {
        return prepare(payload).call(cloud)
    }
}

fun <S, E> RESTCallDescription<Unit, S, E>.prepare(): PreparedRESTCall<S, E> = prepare(Unit)

suspend fun <S, E> RESTCallDescription<Unit, S, E>.call(cloud: AuthenticatedCloud): RESTResponse<S, E> =
        call(Unit, cloud)

sealed class RESTBody<R : Any, T : Any> {
    abstract val ref: TypeReference<T>

    data class BoundToSubProperty<R : Any, T : Any>(
            val property: KProperty1<R, *>,
            override val ref: TypeReference<T>
    ) : RESTBody<R, T>()

    data class BoundToEntireRequest<R : Any>(
            override val ref: TypeReference<R>
    ) : RESTBody<R, R>()
}

data class RESTPath<R : Any>(val basePath: String, val segments: List<RESTPathSegment<R>>)

sealed class RESTPathSegment<R : Any> {
    data class Simple<R : Any>(val text: String) : RESTPathSegment<R>()
    data class Property<R : Any, P>(val property: KProperty1<R, P>) : RESTPathSegment<R>()
    class Remaining<R : Any> : RESTPathSegment<R>()
}

inline fun <reified T : Any, reified E : Any> preparedCallWithJsonOutput(
        endpoint: String,
        mapper: ObjectMapper = HttpClient.defaultMapper,
        crossinline configureBody: BoundRequestBuilder.() -> Unit = {}
): PreparedRESTCall<T, E> {
    val successRef = mapper.readerFor(jacksonTypeRef<T>())
    val errorRef = mapper.readerFor(jacksonTypeRef<E>())

    return object : PreparedRESTCall<T, E>(endpoint) {
        override fun BoundRequestBuilder.configure() {
            configureBody()
        }

        override fun deserializeSuccess(response: Response): T {
            return successRef.readValue(response.responseBody)
        }

        override fun deserializeError(response: Response): E? {
            return errorRef.readValue(response.responseBody)
        }
    }
}


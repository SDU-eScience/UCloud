package dk.sdu.cloud.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.netty.handler.codec.http.HttpMethod
import org.asynchttpclient.BoundRequestBuilder
import org.asynchttpclient.Response
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

data class RESTCallDescription<R : Any, S : Any, E : Any>(
    val method: HttpMethod,
    val path: RESTPath<R>,
    val body: RESTBody<R, *>?,
    val params: RESTParams<R>?,
    val requestType: KClass<R>,
    val responseTypeSuccess: KClass<S>,
    val responseTypeFailure: KClass<E>,
    val deserializerSuccess: ObjectReader,
    val deserializerError: ObjectReader,
    val owner: ServiceDescription,
    var fullName: String?,
    val requestConfiguration: (BoundRequestBuilder.(R) -> Unit)? = null
) {
    init {
        if (fullName == null) {
            log.info("RESTCallDescription (${path.toKtorTemplate(true)}) has no name")
        }
    }

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

        val queryPathMap = params?.let {
            it.parameters.mapNotNull {
                when (it) {
                    is RESTQueryParameter.Property<R, *> -> {
                        it.property.get(payload)?.let { value ->
                            Pair(it.property.name, value.toString())
                        }
                    }
                }
            }.toMap()
        } ?: emptyMap()

        fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")

        val queryPath = queryPathMap
            .map { it.key.urlEncode() + "=" + it.value.urlEncode() }
            .joinToString("&")
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" } ?: ""

        val resolvedPath = path.basePath.removeSuffix("/") + "/" + primaryPath + queryPath
        return object : PreparedRESTCall<S, E>(resolvedPath, owner) {
            override fun deserializeSuccess(response: Response): S {
                return if (responseTypeSuccess == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    Unit as S
                } else {
                    deserializerSuccess.readValue(response.responseBody)
                }
            }

            override fun deserializeError(response: Response): E? {
                return if (responseTypeFailure == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    Unit as E
                } else {
                    deserializerError.readValue(response.responseBody)
                }
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

            override fun toString(): String {
                val name = (fullName ?: "${method.name()} $resolvedPath") + " (${owner.name}@${owner.version})"
                return "$name($payload)"
            }
        }
    }

    suspend fun call(
        payload: R,
        cloud: AuthenticatedCloud,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000
    ): RESTResponse<S, E> {
        return prepare(payload).call(cloud, requestTimeout, readTimeout)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RESTDescriptions::class.java)
    }
}

fun <S : Any, E : Any> RESTCallDescription<Unit, S, E>.prepare(): PreparedRESTCall<S, E> = prepare(Unit)

suspend fun <S : Any, E : Any> RESTCallDescription<Unit, S, E>.call(cloud: AuthenticatedCloud): RESTResponse<S, E> =
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

data class RESTParams<R : Any>(val parameters: List<RESTQueryParameter<R>>)

sealed class RESTQueryParameter<R : Any> {
    data class Property<R : Any, P>(val property: KProperty1<R, P>) : RESTQueryParameter<R>()
}

inline fun <reified T : Any, reified E : Any> preparedCallWithJsonOutput(
    endpoint: String,
    owner: ServiceDescription,
    mapper: ObjectMapper = HttpClient.defaultMapper,
    crossinline configureBody: BoundRequestBuilder.() -> Unit = {}
): PreparedRESTCall<T, E> {
    val successRef = mapper.readerFor(jacksonTypeRef<T>())
    val errorRef = mapper.readerFor(jacksonTypeRef<E>())

    return object : PreparedRESTCall<T, E>(endpoint, owner) {
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


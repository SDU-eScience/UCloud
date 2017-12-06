package org.esciencecloud.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.ktor.http.HttpMethod
import org.asynchttpclient.BoundRequestBuilder
import org.asynchttpclient.Response
import org.esciencecloud.abc.stackTraceToString
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.asynchttp.addBasicAuth
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

// TODO This should be moved out. Just keeping it here for experiment

sealed class RESTResponse<out T, out E> {
    abstract val response: Response

    val status: Int get() = response.statusCode
    val statusText: String get() = response.statusText
    val rawResponseBody: String get() = response.responseBody

    data class Ok<out T, out E>(override val response: Response, val result: T) : RESTResponse<T, E>()
    data class Err<out T, out E>(override val response: Response, val error: E? = null) : RESTResponse<T, E>()
}

abstract class PreparedRESTCall<out T, out E>(resolvedEndpoint: String) {
    val resolvedEndpoint = resolvedEndpoint.removePrefix("/")

    companion object {
        private val log = LoggerFactory.getLogger(PreparedRESTCall::class.java)
    }

    abstract fun BoundRequestBuilder.configure()
    abstract fun deserializeSuccess(response: Response): T
    abstract fun deserializeError(response: Response): E?

    suspend fun call(context: AuthenticatedCloud): RESTResponse<T, E> {
        val url = "${context.parent.endpoint}/$resolvedEndpoint"
        val resp = HttpClient.get(url) {
            context.apply { configureCall() }
            configure()
        }

        // TODO FIXME The stackTraceToString does not exist in the correct module!
        return if (resp.statusCode in 200..299) {
            val result = try {
                deserializeSuccess(resp)
            } catch (ex: Exception) {
                log.warn("Caught exception while attempting to deserialize a _successful_ message!")
                log.warn("Exception follows: ${ex.stackTraceToString()}")
                null
            }

            if (result != null) {
                RESTResponse.Ok(resp, result)
            } else {
                log.warn("Unable to deserialize _successful_ message!")
                RESTResponse.Err(resp)
            }
        } else {
            val error = try {
                deserializeError(resp)
            } catch (ex: Exception) {
                log.warn("Caught exception while attempting to deserialize an unsuccessful message!")
                log.warn("Exception follows: ${ex.stackTraceToString()}")
                null
            }

            RESTResponse.Err(resp, error)
        }
    }
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

class EScienceCloud(endpoint: String) {
    val endpoint = endpoint.removeSuffix("/")
}

interface AuthenticatedCloud {
    val parent: EScienceCloud
    fun BoundRequestBuilder.configureCall()
}

class BasicAuthenticatedCloud(
        override val parent: EScienceCloud,
        val username: String,
        val password: String
) : AuthenticatedCloud {
    override fun BoundRequestBuilder.configureCall() {
        addBasicAuth(username, password)
    }
}

fun EScienceCloud.basicAuth(username: String, password: String) = BasicAuthenticatedCloud(this, username, password)

data class RESTCallDescription<R : Any, S, E>(
        val method: HttpMethod,
        val path: RESTPath<R>,
        val requestType: KClass<R>,
        val deserializerSuccess: ObjectReader,
        val deserializerError: ObjectReader,
        val requestConfiguration: BoundRequestBuilder.() -> Unit = {}
) {
    fun prepare(payload: R): PreparedRESTCall<S, E> {
        val primaryPath = path.segments.joinToString("/") {
            when (it) {
                is RESTPathSegment.Simple -> it.text
                is RESTPathSegment.Remaining -> ""
                is RESTPathSegment.Property<R, *> -> {
                    val value = it.property.get(payload)
                    value?.toString() ?: ""
                }
            }
        }

        val resolvedPath = path.basePath.removeSuffix("/") + "/" + primaryPath
        return object : PreparedRESTCall<S, E>(resolvedPath) {
            override fun deserializeSuccess(response: Response): S {
                return deserializerSuccess.readValue(response.responseBody)
            }

            override fun deserializeError(response: Response): E? {
                return deserializerError.readValue(response.responseBody)
            }

            override fun BoundRequestBuilder.configure() {
                requestConfiguration()
            }
        }
    }
}

data class RESTPath<R : Any>(val basePath: String, val segments: List<RESTPathSegment<R>>)

sealed class RESTPathSegment<R : Any> {
    data class Simple<R : Any>(val text: String) : RESTPathSegment<R>()
    data class Property<R : Any, P>(val property: KProperty1<R, P>) : RESTPathSegment<R>()
    class Remaining<R : Any> : RESTPathSegment<R>()
}

typealias KafkaCallDescription<R> = RESTCallDescription<R, GatewayJobResponse, GatewayJobResponse>
typealias KafkaCallDescriptionBundle<R> = List<RESTCallDescription<out R, GatewayJobResponse, GatewayJobResponse>>

// Needs to be exported to clients of GW. We purposefully remove _all_ references to Kafka here.
enum class JobStatus {
    STARTED,
    COMPLETE,
    ERROR
}

class GatewayJobResponse private constructor(
        val status: JobStatus,
        val jobId: String?,
        val offset: Long?,
        val partition: Int?,
        val timestamp: Long?
) {
    companion object {
        private val error by lazy { GatewayJobResponse(JobStatus.ERROR, null, null, null, null) }
        fun started(jobId: String, offset: Long, partition: Int, timestamp: Long) =
                GatewayJobResponse(JobStatus.STARTED, jobId, offset, partition, timestamp)
        fun error() = error
    }
}

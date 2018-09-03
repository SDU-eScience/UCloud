package dk.sdu.cloud.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readText
import io.ktor.http.isSuccess
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ConnectException

internal val httpClient = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = JacksonSerializer()
    }
}

private fun Exception.stackTraceToString(): String = StringWriter().apply {
    printStackTrace(PrintWriter(this))
}.toString()

sealed class RESTResponse<out T, out E> {
    abstract val response: HttpResponse

    val status: Int get() = response.status.value
    val statusText: String get() = response.status.description
    val rawResponseBody: String get() = runBlocking { response.readText() }

    data class Ok<out T, out E>(override val response: HttpResponse, val result: T) : RESTResponse<T, E>()
    data class Err<out T, out E>(override val response: HttpResponse, val error: E? = null) : RESTResponse<T, E>()
}

abstract class PreparedRESTCall<out T, out E>(resolvedEndpoint: String, private val namespace: String) {
    private val resolvedEndpoint = resolvedEndpoint.removePrefix("/")

    companion object {
        private val log = LoggerFactory.getLogger(PreparedRESTCall::class.java)
    }

    abstract fun HttpRequestBuilder.configure()
    abstract fun deserializeSuccess(response: HttpResponse): T
    abstract fun deserializeError(response: HttpResponse): E?

    suspend fun call(context: AuthenticatedCloud): RESTResponse<T, E> {
        // While loop is used for retries in case of connection issues.
        //
        // When a connection issue is encountered the CloudContext is allowed to attempt reconfiguration. If it deems
        // it successful another attempt is made, otherwise the exception is thrown to the client.
        var attempts = 0
        while (true) {
            attempts++
            if (attempts == 5) throw ConnectException("Too many retries!")

            val endpoint = context.parent.resolveEndpoint(namespace).removeSuffix("/")
            val url = "$endpoint/$resolvedEndpoint"
            val resp = try {
                httpClient.get<HttpResponse>(url) {
                    context.apply { configureCall() }
                    configure()
                    log.debug("Making call: $url: ${this@PreparedRESTCall}")
                }
            } catch (ex: ConnectException) {
                log.debug("ConnectException: ${ex.message}")
                val shouldRetry = context.parent.tryReconfigurationOnConnectException(this, ex)
                if (shouldRetry) continue else throw ex
            }

//            log.debug("Retrieved the following HTTP response: $resp")
            val result: RESTResponse<T, E> = if (resp.status.isSuccess()) {
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
            } else if (resp.status.value in 500..599) {
                log.info("Caught server error!")
                log.info("Call was: $this, response was: $resp")
                delay(250)

                val ex = ConnectException("Remote server had an internal server error (${resp.status})")
                val shouldRetry = context.parent.tryReconfigurationOnConnectException(this, ex)
                if (shouldRetry) continue else throw ex
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
            log.debug("Call result: $result")
            return result
        }
    }
}

interface CloudContext {
    fun resolveEndpoint(namespace: String): String
    fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean
}

class SDUCloud(private val endpoint: String) : CloudContext {
    override fun resolveEndpoint(namespace: String): String = endpoint

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        // There is not much to do if gateway is not responding
        return false
    }
}

interface AuthenticatedCloud {
    val parent: CloudContext
    fun HttpRequestBuilder.configureCall()
}

class JWTAuthenticatedCloud(
    override val parent: CloudContext,
    val token: String // token is kept public such that tools may check if JWT has expired
) : AuthenticatedCloud {
    override fun HttpRequestBuilder.configureCall() {
        header("Authorization", "Bearer $token")
    }
}

fun CloudContext.jwtAuth(token: String) = JWTAuthenticatedCloud(this, token)

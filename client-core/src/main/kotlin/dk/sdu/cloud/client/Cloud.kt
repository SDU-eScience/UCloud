package dk.sdu.cloud.client

import kotlinx.coroutines.experimental.delay
import org.asynchttpclient.BoundRequestBuilder
import org.asynchttpclient.Response
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ConnectException

private fun Exception.stackTraceToString(): String = StringWriter().apply {
    printStackTrace(PrintWriter(this))
}.toString()

sealed class RESTResponse<out T, out E> {
    abstract val response: Response

    val status: Int get() = response.statusCode
    val statusText: String get() = response.statusText
    val rawResponseBody: String get() = response.responseBody

    data class Ok<out T, out E>(override val response: Response, val result: T) : RESTResponse<T, E>()
    data class Err<out T, out E>(override val response: Response, val error: E? = null) : RESTResponse<T, E>()
}

abstract class PreparedRESTCall<out T, out E>(resolvedEndpoint: String, val owner: ServiceDescription) {
    private val resolvedEndpoint = resolvedEndpoint.removePrefix("/")

    companion object {
        private val log = LoggerFactory.getLogger(PreparedRESTCall::class.java)
    }

    abstract fun BoundRequestBuilder.configure()
    abstract fun deserializeSuccess(response: Response): T
    abstract fun deserializeError(response: Response): E?

    suspend fun call(
        context: AuthenticatedCloud,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000
    ): RESTResponse<T, E> {
        // While loop is used for retries in case of connection issues.
        //
        // When a connection issue is encountered the CloudContext is allowed to attempt reconfiguration. If it deems
        // it successful another attempt is made, otherwise the exception is thrown to the client.
        var attempts = 0
        while (true) {
            attempts++
            if (attempts == 5) throw ConnectException("Too many retries!")

            val endpoint = context.parent.resolveEndpoint(this).removeSuffix("/")
            val url = "$endpoint/$resolvedEndpoint"
            val resp = try {
                HttpClient.get(url, requestTimeout, readTimeout) {
                    context.apply { configureCall() }
                    configure()
                    log.debug("Making call: $url: ${this@PreparedRESTCall}")
                }
            } catch (ex: ConnectException) {
                log.debug("ConnectException: ${ex.message}")
                val shouldRetry = context.parent.tryReconfigurationOnConnectException(this, ex)
                if (shouldRetry) continue else throw ex
            }

            log.debug("Retrieved the following HTTP response: $resp")
            val result: RESTResponse<T, E> = if (resp.statusCode in 200..299) {
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
            } else if (resp.statusCode in 500..599) {
                log.info("Caught server error!")
                log.info("Call was: $this, response was: $resp")
                delay(250)

                val ex = ConnectException("Remote server had an internal server error (${resp.statusText})")
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
    fun resolveEndpoint(call: PreparedRESTCall<*, *>): String
    fun resolveEndpoint(service: ServiceDescription): String
    fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean
}

class SDUCloud(private val endpoint: String) : CloudContext {
    override fun resolveEndpoint(call: PreparedRESTCall<*, *>): String = endpoint
    override fun resolveEndpoint(service: ServiceDescription): String = endpoint

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        // There is not much to do if gateway is not responding
        return false
    }
}

interface AuthenticatedCloud {
    val parent: CloudContext
    fun BoundRequestBuilder.configureCall()
}

class JWTAuthenticatedCloud(
    override val parent: CloudContext,
    val token: String // token is kept public such that tools may check if JWT has expired
) : AuthenticatedCloud {
    override fun BoundRequestBuilder.configureCall() {
        setHeader("Authorization", "Bearer $token")
    }
}

fun CloudContext.jwtAuth(token: String) = JWTAuthenticatedCloud(this, token)

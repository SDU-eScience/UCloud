package dk.sdu.cloud.client

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

    suspend fun call(context: AuthenticatedCloud): RESTResponse<T, E> {
        // While loop is used for retries in case of connection issues.
        //
        // When a connection issue is encountered the CloudContext is allowed to attempt reconfiguration. If it deems
        // it successful another attempt is made, otherwise the exception is thrown to the client.
        while (true) {
            val endpoint = context.parent.resolveEndpoint(this).removeSuffix("/")
            val url = "$endpoint/$resolvedEndpoint"
            val resp = try {
                HttpClient.get(url) {
                    context.apply { configureCall() }
                    configure()

                    log.debug("Making call: $url: ${this}")
                }
            } catch (ex: ConnectException) {
                log.debug("ConnectException: ${ex.message}")
                val shouldRetry = context.parent.tryReconfigurationOnConnectException(this, ex)
                if (shouldRetry) {
                    log.debug("Retrying")
                    continue
                } else {
                    log.debug("Exiting")
                    throw ex
                }
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

@Deprecated(message = "Use JWT auth instead", replaceWith = ReplaceWith("JWTAuthenticatedCloud(token)"))
class BasicAuthenticatedCloud(
        override val parent: CloudContext,
        private val username: String,
        private val password: String
) : AuthenticatedCloud {
    override fun BoundRequestBuilder.configureCall() {
        addBasicAuth(username, password)
    }
}

class JWTAuthenticatedCloud(
        override val parent: CloudContext,
        val token: String // token is kept public such that tools may check if JWT has expired
) : AuthenticatedCloud {
    override fun BoundRequestBuilder.configureCall() {
        setHeader("Authorization", "Bearer $token")
    }
}

@Deprecated(message = "Use JWT auth instead", replaceWith = ReplaceWith("jwtAuth", "dk.sdu.cloud.client.jwtAuth"))
fun CloudContext.basicAuth(username: String, password: String) = BasicAuthenticatedCloud(this, username, password)

fun CloudContext.jwtAuth(token: String) = JWTAuthenticatedCloud(this, token)

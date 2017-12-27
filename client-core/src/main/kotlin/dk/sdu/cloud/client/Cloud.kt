package dk.sdu.cloud.client

import org.asynchttpclient.BoundRequestBuilder
import org.asynchttpclient.Response
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter

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

class SDUCloud(endpoint: String) {
    val endpoint = endpoint.removeSuffix("/")
}

interface AuthenticatedCloud {
    val parent: SDUCloud
    fun BoundRequestBuilder.configureCall()
}

@Deprecated(message = "Use JWT auth instead", replaceWith = ReplaceWith("JWTAuthenticatedCloud(token)"))
class BasicAuthenticatedCloud(
        override val parent: SDUCloud,
        val username: String,
        val password: String
) : AuthenticatedCloud {
    override fun BoundRequestBuilder.configureCall() {
        addBasicAuth(username, password)
    }
}

class JWTAuthenticatedCloud(
        override val parent: SDUCloud,
        val token: String
) : AuthenticatedCloud {
    override fun BoundRequestBuilder.configureCall() {
        setHeader("Authorization", "Bearer $token")
    }
}

@Deprecated(message = "Use JWT auth instead", replaceWith = ReplaceWith("jwtAuth", "dk.sdu.cloud.client.jwtAuth"))
fun SDUCloud.basicAuth(username: String, password: String) = BasicAuthenticatedCloud(this, username, password)

fun SDUCloud.jwtAuth(token: String) = JWTAuthenticatedCloud(this, token)

package dk.sdu.cloud.client

import dk.sdu.cloud.service.stackTraceToString
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.response.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.experimental.delay
import org.slf4j.LoggerFactory
import java.net.ConnectException

private const val DELAY_TIME = 250
private const val INTERNAL_ERROR_CODE_START = 500
private const val INTERNAL_ERROR_CODE_STOP = 599
private const val NUMBER_OF_ATTEMPTS = 5

abstract class PreparedRESTCall<out T, out E>(private val namespace: String) {
    private var resolvedEndpoint: String? = null

    constructor(resolvedEndpoint: String, namespace: String) : this(namespace) {
        this.resolvedEndpoint = resolvedEndpoint.removePrefix("/")
    }

    companion object {
        private val log = LoggerFactory.getLogger(PreparedRESTCall::class.java)
    }

    abstract fun HttpRequestBuilder.configure()
    abstract fun deserializeSuccess(response: HttpResponse): T
    abstract fun deserializeError(response: HttpResponse): E?

    protected open fun resolveEndpoint(): String {
        return resolvedEndpoint ?: throw IllegalStateException("Missing resolved endpoint. " +
                "Must implement resolveEndpoint()")
    }

    suspend fun call(context: AuthenticatedCloud): RESTResponse<T, E> {
        // While loop is used for retries in case of connection issues.
        //
        // When a connection issue is encountered the CloudContext is allowed to attempt reconfiguration. If it deems
        // it successful another attempt is made, otherwise the exception is thrown to the client.
        var attempts = 0
        while (true) {
            attempts++
            if (attempts == NUMBER_OF_ATTEMPTS) throw ConnectException("Too many retries!")

            val endpoint = context.parent.resolveEndpoint(namespace).removeSuffix("/")
            val resolvedEndpoint = resolveEndpoint()

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
            } else if (resp.status.value in INTERNAL_ERROR_CODE_START..INTERNAL_ERROR_CODE_STOP) {
                log.info("Caught server error!")
                log.info("Call was: $this, response was: $resp")
                delay(DELAY_TIME)

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

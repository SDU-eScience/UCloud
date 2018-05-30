package dk.sdu.cloud.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.BoundRequestBuilder
import org.asynchttpclient.DefaultAsyncHttpClient
import org.asynchttpclient.Response
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine

object HttpClient {
    private val httpClient = DefaultAsyncHttpClient()
    var defaultMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    suspend fun get(
        url: String,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000,
        options: (BoundRequestBuilder.() -> Unit)? = null
    ): Response {
        val builder = httpClient.prepareGet(url).setRequestTimeout(requestTimeout).setReadTimeout(readTimeout)
        if (options != null) builder.options()
        return builder.async()
    }

    suspend fun put(
        url: String,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000,
        options: (BoundRequestBuilder.() -> Unit)? = null
    ): Response {
        val builder = httpClient.preparePut(url).setRequestTimeout(requestTimeout).setReadTimeout(readTimeout)
        if (options != null) builder.options()
        return builder.async()
    }

    suspend fun patch(
        url: String,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000,
        options: (BoundRequestBuilder.() -> Unit)? = null
    ): Response {
        val builder = httpClient.preparePatch(url).setRequestTimeout(requestTimeout).setReadTimeout(readTimeout)
        if (options != null) builder.options()
        return builder.async()
    }

    suspend fun post(
        url: String,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000,
        options: (BoundRequestBuilder.() -> Unit)? = null
    ): Response {
        val builder = httpClient.preparePost(url).setRequestTimeout(requestTimeout).setReadTimeout(readTimeout)
        if (options != null) builder.options()
        return builder.async()
    }

    suspend fun delete(
        url: String,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000,
        options: (BoundRequestBuilder.() -> Unit)? = null
    ): Response {
        val builder = httpClient.prepareDelete(url).setRequestTimeout(requestTimeout).setReadTimeout(readTimeout)
        if (options != null) builder.options()
        return builder.async()
    }

    suspend fun options(
        url: String,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000,
        options: (BoundRequestBuilder.() -> Unit)? = null
    ): Response {
        val builder = httpClient.prepareOptions(url).setRequestTimeout(requestTimeout).setReadTimeout(readTimeout)
        if (options != null) builder.options()
        return builder.async()
    }

    suspend fun trace(
        url: String,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000,
        options: (BoundRequestBuilder.() -> Unit)? = null
    ): Response {
        val builder = httpClient.prepareTrace(url).setRequestTimeout(requestTimeout).setReadTimeout(readTimeout)
        if (options != null) builder.options()
        return builder.async()
    }

    suspend fun head(
        url: String,
        requestTimeout: Int = -1,
        readTimeout: Int = 60_000,
        options: (BoundRequestBuilder.() -> Unit)? = null
    ): Response {
        val builder = httpClient.prepareHead(url).setRequestTimeout(requestTimeout).setReadTimeout(readTimeout)
        if (options != null) builder.options()
        return builder.async()
    }
}

fun BoundRequestBuilder.addBasicAuth(username: String, password: String): BoundRequestBuilder {
    val authString = String(Base64.getEncoder().encode("$username:$password".toByteArray()))
    addHeader("Authorization", "Basic $authString")
    return this
}

fun BoundRequestBuilder.setJsonBody(payload: Any?, mapper: ObjectWriter = HttpClient.defaultMapper.writer()) {
    setHeader("Content-Type", "application/json")
    setBody(mapper.writeValueAsString(payload))
}

suspend fun BoundRequestBuilder.async(): Response = suspendCoroutine { continuation ->
    execute(object : AsyncCompletionHandler<Response>() {
        override fun onCompleted(response: Response): Response {
            continuation.resume(response)
            return response
        }

        override fun onThrowable(t: Throwable) {
            continuation.resumeWithException(t)
        }
    })
}

inline fun <reified T : Any> Response.asJson(mapper: ObjectMapper = HttpClient.defaultMapper): T =
    mapper.readValue(responseBody)

fun Response.asDynamicJson(mapper: ObjectMapper = HttpClient.defaultMapper): JsonNode =
    mapper.readTree(responseBody)


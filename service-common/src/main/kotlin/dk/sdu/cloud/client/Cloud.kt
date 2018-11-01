package dk.sdu.cloud.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readText
import kotlinx.coroutines.runBlocking
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ConnectException
import java.util.*

internal val httpClient = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = JacksonSerializer()
    }
}

sealed class RESTResponse<out T, out E> {
    abstract val response: HttpResponse

    val status: Int get() = response.status.value
    val statusText: String get() = response.status.description
    val rawResponseBody: String get() = runBlocking { response.readText() }

    data class Ok<out T, out E>(override val response: HttpResponse, val result: T) : RESTResponse<T, E>() {
        override fun toString(): String = "OK(${response.status}, $result)"
    }

    data class Err<out T, out E>(override val response: HttpResponse, val error: E? = null) : RESTResponse<T, E>() {
        override fun toString(): String = "Err(${response.status}, $error)"
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

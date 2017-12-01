package org.esciencecloud.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import kotlinx.coroutines.experimental.runBlocking
import org.asynchttpclient.BoundRequestBuilder
import org.asynchttpclient.Response
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.asynchttp.addBasicAuth

sealed class RESTResponse<out T, out E> {
    abstract val response: Response

    val status: Int get() = response.statusCode
    val statusText: String get() = response.statusText
    val rawResponseBody: String get() = response.responseBody

    data class Ok<out T, out E>(override val response: Response, val result: T) : RESTResponse<T, E>()
    data class Err<out T, out E>(override val response: Response, val error: E) : RESTResponse<T, E>()
}

abstract class PreparedRESTCall<out T, out E>(val resolvedEndpoint: String) {
    abstract fun BoundRequestBuilder.configure()
    abstract fun deserializeSuccess(response: Response): T
    abstract fun deserializeError(response: Response): E

    suspend fun call(context: AuthenticatedCloud): RESTResponse<T, E> {
        val resp = HttpClient.get("${context.parent.endpoint}/$resolvedEndpoint") {
            context.apply { configureCall() }
            configure()
        }

        return if (resp.statusCode in 200..299) {
            RESTResponse.Ok(resp, deserializeSuccess(resp))
        } else {
            RESTResponse.Err(resp, deserializeError(resp))
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

        override fun deserializeError(response: Response): E {
            return errorRef.readValue(response.responseBody)
        }
    }
}

class EScienceCloud(val endpoint: String)

interface AuthenticatedCloud {
    val parent: EScienceCloud
    fun BoundRequestBuilder.configureCall()
}

class BasicAuthenticatedCloud(
        override val parent: EScienceCloud,
        val username: String,
        val password: String
) : AuthenticatedCloud{
    override fun BoundRequestBuilder.configureCall() {
        addBasicAuth(username, password)
    }
}

fun EScienceCloud.basicAuth(username: String, password: String) = BasicAuthenticatedCloud(this, username, password)

data class Test(val f: String)
data class TestError(val f: String)

object HPC {
    fun listTest(): PreparedRESTCall<List<Test>, TestError> = preparedCallWithJsonOutput("/hpc/tests")
    fun findAppsByName(name: String): PreparedRESTCall<List<Test>, TestError> =
            preparedCallWithJsonOutput("/hpc/apps/$name")

    fun findAppByNameAndVersion(name: String, version: String): PreparedRESTCall<Test, TestError> =
            preparedCallWithJsonOutput("/hpc/apps/$name/$version")
}


fun main(args: Array<String>) = runBlocking {
    val cloud = EScienceCloud("https://cloud.sdu.dk/api")
    val bound = cloud.basicAuth("rods", "rods")
    val result = HPC.listTest().call(bound)

    when (result) {
        is RESTResponse.Ok -> {
            result.result.forEach {

            }
        }
    }
}

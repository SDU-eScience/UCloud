package dk.sdu.cloud.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectReader
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityScope
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.response.HttpResponse
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.experimental.io.jvm.javaio.toInputStream
import java.net.URLEncoder
import kotlin.reflect.KProperty1

data class RESTCallDescription<Request : Any, Success : Any, Error : Any, AuditEntry : Any>(
    val method: HttpMethod,
    val path: RESTPath<Request>,
    val body: RESTBody<Request, *>?,
    val params: RESTParams<Request>?,
    val auth: RESTAuth,

    val requestType: TypeReference<Request>,
    val responseTypeSuccess: TypeReference<Success>,
    val responseTypeFailure: TypeReference<Error>,
    val normalizedRequestTypeForAudit: TypeReference<AuditEntry>,

    val deserializerSuccess: ObjectReader,
    val deserializerError: ObjectReader,

    val namespace: String,
    var fullName: String,

    val requestConfiguration: (HttpRequestBuilder.(Request) -> Unit)? = null
) {
    val requiredAuthScope: SecurityScope

    init {
        try {
            requiredAuthScope = SecurityScope.parseFromString(fullName + ':' + auth.access.scopeName)
        } catch (ex: Exception) {
            throw IllegalArgumentException("Invalid namespace or request name")
        }
    }

    fun prepare(payload: Request): PreparedRESTCall<Success, Error> {
        val primaryPath = path.segments.mapNotNull {
            when (it) {
                is RESTPathSegment.Simple -> it.text
                is RESTPathSegment.Remaining -> ""
                is RESTPathSegment.Property<Request, *> -> {
                    val value = it.property.get(payload)
                    value?.toString()
                }
            }
        }.joinToString("/")

        val queryPathMap = params?.parameters?.mapNotNull {
            when (it) {
                is RESTQueryParameter.Property<Request, *> -> {
                    it.property.get(payload)?.let { value ->
                        Pair(it.property.name, value.toString())
                    }
                }
            }
        }?.toMap() ?: emptyMap()

        fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")

        val queryPath = queryPathMap
            .map { it.key.urlEncode() + "=" + it.value.urlEncode() }
            .joinToString("&")
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" } ?: ""

        val resolvedPath = path.basePath.removeSuffix("/") + "/" + primaryPath + queryPath
        return object : PreparedRESTCall<Success, Error>(resolvedPath, namespace) {
            override fun deserializeSuccess(response: HttpResponse): Success {
                return if (responseTypeSuccess.type == Unit::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    Unit as Success
                } else {
                    // TODO Don't use blocking API
                    // TODO Don't use blocking API
                    // TODO Don't use blocking API
                    deserializerSuccess.readValue(response.content.toInputStream())
                }
            }

            override fun deserializeError(response: HttpResponse): Error? {
                return if (responseTypeFailure.type == Unit::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    Unit as Error
                } else {
                    // TODO Don't use blocking API
                    // TODO Don't use blocking API
                    // TODO Don't use blocking API
                    deserializerError.readValue(response.content.toInputStream())
                }
            }

            override fun HttpRequestBuilder.configure() {
                method = this@RESTCallDescription.method
                requestConfiguration?.invoke(this, payload)
                val requestBodyDescription = this@RESTCallDescription.body
                when (requestBodyDescription) {
                    is RESTBody.BoundToEntireRequest<*> -> {
                        body = TextContent(defaultMapper.writeValueAsString(payload), ContentType.Application.Json)
                    }

                    is RESTBody.BoundToSubProperty -> {
                        contentType(ContentType.Application.Json)
                        body = TextContent(
                            defaultMapper.writeValueAsString(
                                requestBodyDescription.property.get(payload)!!
                            ),
                            ContentType.Application.Json
                        )
                    }
                }
            }

            override fun toString(): String {
                val name = "$fullName ($namespace)"
                return "$name($payload)"
            }
        }
    }

    suspend fun call(
        payload: Request,
        cloud: AuthenticatedCloud
    ): RESTResponse<Success, Error> {
        return prepare(payload).call(cloud)
    }
}

fun <S : Any, E : Any> RESTCallDescription<Unit, S, E, *>.prepare(): PreparedRESTCall<S, E> = prepare(Unit)

suspend fun <S : Any, E : Any> RESTCallDescription<Unit, S, E, *>.call(cloud: AuthenticatedCloud): RESTResponse<S, E> =
    call(Unit, cloud)

sealed class RESTBody<Request : Any, Property : Any> {
    abstract val ref: TypeReference<Property>

    data class BoundToSubProperty<Request : Any, Property : Any>(
        val property: KProperty1<Request, *>,
        override val ref: TypeReference<Property>
    ) : RESTBody<Request, Property>()

    data class BoundToEntireRequest<Request : Any>(
        override val ref: TypeReference<Request>
    ) : RESTBody<Request, Request>()
}

data class RESTPath<R : Any>(val basePath: String, val segments: List<RESTPathSegment<R>>)

sealed class RESTPathSegment<Request : Any> {
    data class Simple<Request : Any>(val text: String) : RESTPathSegment<Request>()
    data class Property<Request : Any, Property>(val property: KProperty1<Request, Property>) :
        RESTPathSegment<Request>()

    class Remaining<Request : Any> : RESTPathSegment<Request>()
}

data class RESTParams<Request : Any>(val parameters: List<RESTQueryParameter<Request>>)

sealed class RESTQueryParameter<Request : Any> {
    data class Property<Request : Any, Property>(val property: KProperty1<Request, Property>) :
        RESTQueryParameter<Request>()
}

data class RESTAuth(
    val roles: Set<Role>,
    val access: AccessRight
)

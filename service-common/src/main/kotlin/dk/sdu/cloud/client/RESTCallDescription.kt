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
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import java.net.URLEncoder
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObjectInstance

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

    fun prepare(payload: Request): PreparedRESTCall<Success, Error> = DescriptionPreparedRESTCall(payload, this)

    suspend fun call(
        payload: Request,
        cloud: AuthenticatedCloud
    ): RESTResponse<Success, Error> {
        return prepare(payload).call(cloud)
    }
}

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

internal class DescriptionPreparedRESTCall<Request : Any, Success : Any, Error : Any>(
    private val payload: Request,
    private val description: RESTCallDescription<Request, Success, Error, *>
) : PreparedRESTCall<Success, Error>(description.namespace) {
    override fun deserializeSuccess(response: HttpResponse): Success {
        return if (description.responseTypeSuccess.type == Unit::class.java) {
            @Suppress("UNCHECKED_CAST")
            Unit as Success
        } else {
            // TODO Don't use blocking API
            description.deserializerSuccess.readValue(response.content.toInputStream())
        }
    }

    override fun deserializeError(response: HttpResponse): Error? {
        return if (description.responseTypeFailure.type == Unit::class.java) {
            @Suppress("UNCHECKED_CAST")
            Unit as Error
        } else {
            // TODO Don't use blocking API
            description.deserializerError.readValue(response.content.toInputStream())
        }
    }

    private fun KProperty<*>.companionObjectInstance(): Any? = (returnType as? KClass<*>)?.companionObjectInstance
    private fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")

    override fun resolveEndpoint(): String {
        val primaryPath = serializePathSegments()
        val queryPathMap = serializeQueryParameters() ?: emptyMap()
        val queryPath = encodeQueryParamsToString(queryPathMap)

        return description.path.basePath.removeSuffix("/") + "/" + primaryPath + queryPath
    }

    private fun serializePathSegments(): String {
        return description.path.segments.asSequence().mapNotNull {
            when (it) {
                is RESTPathSegment.Simple -> it.text
                is RESTPathSegment.Remaining -> ""
                is RESTPathSegment.Property<Request, *> -> {
                    val value = it.property.get(payload)

                    @Suppress("UNCHECKED_CAST")
                    val marshaller = it.property.companionObjectInstance() as? RequestPathSegmentMarshall<Any?>

                    marshaller?.serializePathSegment(it, value) ?: value?.toString()
                }
            }
        }.joinToString("/")
    }

    private fun serializeQueryParameters(): Map<String, String>? {
        return description.params?.parameters?.mapNotNull {
            when (it) {
                is RESTQueryParameter.Property<Request, *> -> {
                    val value = it.property.get(payload)

                    @Suppress("UNCHECKED_CAST")
                    val marshaller = it.property.companionObjectInstance() as? RequestQueryParamMarshall<Any?>

                    if (marshaller != null) {
                        marshaller.serializeQueryParam(it, value)
                    } else {
                        if (value == null) null
                        else Pair(it.property.name, value.toString())
                    }
                }
            }
        }?.toMap()
    }

    private fun encodeQueryParamsToString(queryPathMap: Map<String, String>): String {
        return queryPathMap
            .map { it.key.urlEncode() + "=" + it.value.urlEncode() }
            .joinToString("&")
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" } ?: ""
    }

    override fun HttpRequestBuilder.configure() {
        method = description.method
        description.requestConfiguration?.invoke(this, payload)

        @Suppress("UNCHECKED_CAST")
        val requestBodyDescription = description.body as RESTBody<Request, Any>?

        if (requestBodyDescription != null) {
            val value = when (requestBodyDescription) {
                is RESTBody.BoundToEntireRequest<*> -> payload
                is RESTBody.BoundToSubProperty -> requestBodyDescription.property.get(payload)!!
            }

            @Suppress("UNCHECKED_CAST")
            val marshaller = value::class.companionObjectInstance as? RequestBodyParamMarshall<Any>

            body = if (marshaller != null) {
                marshaller.serializeBody(description, requestBodyDescription, value)
            } else {
                TextContent(defaultMapper.writeValueAsString(value), ContentType.Application.Json)
            }
        }
    }

    override fun toString(): String {
        val name = "${description.fullName} (${description.namespace})"
        return "$name($payload)"
    }
}

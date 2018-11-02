package dk.sdu.cloud.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTBody
import dk.sdu.cloud.client.RESTCallDescription
import dk.sdu.cloud.client.RESTPathSegment
import dk.sdu.cloud.client.RESTQueryParameter
import dk.sdu.cloud.client.RequestBodyParamMarshall
import dk.sdu.cloud.client.RequestPathSegmentMarshall
import dk.sdu.cloud.client.RequestQueryParamMarshall
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.client.toKtorTemplate
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.featureOrNull
import io.ktor.application.install
import io.ktor.features.conversionService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.request.httpMethod
import io.ktor.request.receiveOrNull
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.routing.method
import io.ktor.routing.route
import io.ktor.util.DataConversionException
import io.ktor.util.pipeline.PipelineContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

private val log = LoggerFactory.getLogger("dk.sdu.cloud.service.ServerSupport")

object RESTServerSupport {
    var defaultMapper: ObjectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(JsonParser.Feature.ALLOW_COMMENTS, true)
        configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
    }
    var allowMissingKafkaHttpLogger = false
}

private var didComplainAboutMissingKafkaLogger = false

private fun warnMissingKafkaLogger() {
    log.warn("implement() calls require the KafkaHttpLogger feature to be installed!")
    log.warn("implement() calls require the KafkaHttpLogger feature to be installed!")
    log.warn("implement() calls require the KafkaHttpLogger feature to be installed!")
    log.debug("Use RESTServerSupport.allowMissingKafkaHttpLogger = true to suppress this message")
    log.warn(
        "NO Kafka logging will be performed without this feature present. The implement " +
                "call was placed here:"
    )
    try {
        throw RuntimeException()
    } catch (ex: RuntimeException) {
        log.warn(ex.stackTraceToString())
    }

    log.warn("We will not complain about further violations.")
    didComplainAboutMissingKafkaLogger = true
}

private fun warnMissingFinalize(restCall: RESTCallDescription<*, *, *, *>) {
    log.warn("implement(${restCall.fullName}) has not been finalized!")
    log.warn("implement(${restCall.fullName}) has not been finalized!")
    log.warn("implement(${restCall.fullName}) has not been finalized!")
    log.warn("implement(${restCall.fullName}) has not been finalized!")
    log.warn("")
    log.warn("No audit entries for ${restCall.fullName} was made.")
    log.warn("We potentially did not send any request (was this handled externally?).")
    log.warn("If the request was handled externally, you must call okContentDeliveredExternally()")
}

private fun resolveCompanionInstanceFromType(type: Type): Any? {
    return when (type) {
        is Class<*> -> {
            if (type.isKotlinClass()) {
                type.kotlin.companionObjectInstance
            } else {
                null
            }
        }

        is ParameterizedType -> {
            resolveCompanionInstanceFromType(type.rawType)
        }

        else -> null
    }
}

private fun <R : Any> RESTHandler<R, *, *, *>.doLogEntry(
    log: Logger,
    payload: R,
    requestToString: (R) -> String = { it.toString() }
) {
    val requestName = restCall.fullName
    val method = call.request.httpMethod.value
    val uri = call.request.uri
    val jobId = call.request.safeJobId
    val causedBy = call.request.causedBy

    val name = "$method $uri ($requestName)"

    log.info("$name jobId=$jobId causedBy=$causedBy payload=${requestToString(payload)}")
}

fun <P : Any, S : Any, E : Any, A : Any> Route.implement(
    restCall: RESTCallDescription<P, S, E, A>,
    logResponse: Boolean = false,
    handler: suspend RESTHandler<P, S, E, A>.(P) -> Unit
) {
    val template = restCall.path.toKtorTemplate(fullyQualified = false)
    route(template) {
        method(restCall.method) {
            val logger = application.featureOrNull(KafkaHttpLogger)
            if (logger == null) {
                if (!didComplainAboutMissingKafkaLogger && !RESTServerSupport.allowMissingKafkaHttpLogger) {
                    warnMissingKafkaLogger()
                }
            } else {
                install(KafkaHttpRouteLogger) {
                    @Suppress("UNCHECKED_CAST")
                    description = restCall as RESTCallDescription<*, *, *, Any>
                }

                install(ImplementAuthCheck) {
                    call(restCall)
                }
            }

            val log = LoggerFactory.getLogger(restCall.fullName)

            handle {
                log.debug("Handling call for ${restCall.fullName}")

                try {
                    @Suppress("UNCHECKED_CAST")
                    val companion =
                        resolveCompanionInstanceFromType(restCall.requestType.type) as? RequestBodyParamMarshall<Any>

                    val payload: P = if (restCall.requestType.type == Unit::class.java) {
                        @Suppress("UNCHECKED_CAST")
                        Unit as P
                    } else {
                        val bodyParseResponse = if (companion != null) {
                            try {
                                companion.deserializeBody(restCall, this)
                            } catch (ex: Exception) {
                                InputParsingResponse.InternalError(ex)
                            }
                        } else {
                            parseRequestBody(call.receiveOrNull(), restCall.body)
                        }

                        val valueFromBody =
                            when (bodyParseResponse) {
                                is InputParsingResponse.Parsed -> bodyParseResponse.result

                                InputParsingResponse.MissingAndNotRequired -> null

                                InputParsingResponse.MissingAndRequired -> {
                                    log.debug("Could not parse payload from body, which was required")
                                    return@handle call.respond(HttpStatusCode.BadRequest)
                                }

                                is InputParsingResponse.InternalError -> {
                                    log.warn("Internal error when deserializing body")
                                    log.warn(bodyParseResponse.cause?.stackTraceToString() ?: "No cause")
                                    return@handle call.respond(HttpStatusCode.InternalServerError)
                                }
                            }

                        // Retrieve argument values from path (if any)
                        val valuesFromPath = try {
                            restCall.path.segments.mapNotNull { it.bindValuesFromCall(call) }.toMap()
                        } catch (ex: IllegalArgumentException) {
                            log.debug("Caught illegal argument exception when constructing values from path")
                            log.debug(ex.stackTraceToString())
                            return@handle call.respond(HttpStatusCode.BadRequest)
                        }

                        // Retrieve arguments from query parameters (if any)
                        val valuesFromParams = try {
                            restCall
                                .params
                                ?.parameters
                                ?.mapNotNull {
                                    it.bindValuesFromCall(call)
                                }?.toMap()
                        } catch (ex: IllegalArgumentException) {
                            log.debug("Caught illegal argument exception when constructing values from params")
                            log.debug(ex.stackTraceToString())
                            return@handle call.respond(HttpStatusCode.BadRequest)
                        } ?: emptyMap()

                        val allValuesFromRequest = valuesFromPath + valuesFromParams

                        if (restCall.body !is RESTBody.BoundToEntireRequest<*>) {
                            val requestClass = restCall.requestType.type as? Class<*> ?: throw IllegalStateException(
                                "${restCall.fullName}'s request type is not a simple class. " +
                                        "This is required."
                            )

                            val requestClassKotlin = if (requestClass.isKotlinClass()) {
                                @Suppress("UNCHECKED_CAST")
                                requestClass.kotlin as KClass<P>
                            } else {
                                throw IllegalStateException(
                                    "${restCall.fullName}'s request type is not a kotlin " +
                                            "class. This is required."
                                )
                            }

                            val constructor =
                                requestClassKotlin.primaryConstructor ?: requestClassKotlin.constructors.single()

                            val resolvedArguments = constructor.parameters.map {
                                val name = it.name ?: run {
                                    throw IllegalStateException(
                                        "Unable to determine name of property in request " +
                                                "type. Please use a data class instead to solve this problem."
                                    )
                                }

                                if (name !in allValuesFromRequest) {
                                    // If not found in path, check if this is param bound to the body
                                    if (restCall.body is RESTBody.BoundToSubProperty<*, *>) {
                                        val body = restCall.body as RESTBody.BoundToSubProperty
                                        if (body.property.name == it.name) {
                                            return@map it to valueFromBody
                                        }
                                    }

                                    // All arguments were collected successfully from the request, but we still
                                    // can't satisfy this constructor parameter. As a result it must be a bug in
                                    // the description.
                                    throw IllegalStateException("The property '$name' was not bound in description!")
                                }

                                it to allValuesFromRequest[name]
                            }.toMap()

                            try {
                                constructor.callBy(resolvedArguments)
                            } catch (ex: IllegalArgumentException) {
                                log.debug("Caught (validation) exception during construction of request object!")
                                log.debug(ex.stackTraceToString())

                                return@handle call.respond(HttpStatusCode.BadRequest)
                            } catch (ex: Exception) {
                                log.warn("Caught exception during construction of request object!")
                                log.warn(ex.stackTraceToString())

                                return@handle call.respond(HttpStatusCode.InternalServerError)
                            }
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            // Request bound to body. We just need to cast the already parsed body. Type safety is
                            // ensured by builder.
                            valueFromBody as P
                        }
                    }

                    val restHandler = RESTHandler(payload, this, logResponse, restCall)
                    restHandler.doLogEntry(log, payload)
                    try {
                        // Call the handler with the payload
                        restHandler.handler(payload)
                    } catch (ex: RPCException) {
                        if (ex.httpStatusCode == HttpStatusCode.InternalServerError) {
                            log.warn("Internal server error in ${restCall.fullName}")
                            log.warn(ex.stackTraceToString())
                        }

                        if (CommonErrorMessage::class.java == restCall.responseTypeFailure.type) {
                            val message =
                                if (ex.httpStatusCode != HttpStatusCode.InternalServerError) ex.why
                                else "Internal Server Error"

                            @Suppress("UNCHECKED_CAST")
                            restHandler.error(CommonErrorMessage(message) as E, ex.httpStatusCode)
                        } else {
                            log.info(ex.stackTraceToString())

                            log.info(
                                "Cannot auto-complete exception message when error type is not " +
                                        "CommonErrorMessage. Please catch exceptions yourself."
                            )
                            call.respond(ex.httpStatusCode)
                        }

                        restHandler.okContentDeliveredExternally()
                    }

                    if (!restHandler.finalized) {
                        warnMissingFinalize(restCall)
                    }
                } catch (ex: Exception) {
                    log.warn(
                        "Caught exception while handling implement. Exception was not caught in the normal " +
                                "handler."
                    )
                    log.warn(ex.stackTraceToString())
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

sealed class InputParsingResponse {
    data class Parsed(val result: Any) : InputParsingResponse()
    object MissingAndRequired : InputParsingResponse()
    object MissingAndNotRequired : InputParsingResponse()
    class InternalError(val cause: Throwable?) : InputParsingResponse()
}

private fun parseRequestBody(requestBody: String?, restBody: RESTBody<*, *>?): InputParsingResponse {
    // We silently ignore a body which is not required
    if (restBody == null) return InputParsingResponse.MissingAndNotRequired

    val hasText = !requestBody.isNullOrEmpty()
    if (!hasText) {
        if (restBody is RESTBody.BoundToSubProperty) {
            return if (restBody.property.returnType.isMarkedNullable) {
                InputParsingResponse.MissingAndNotRequired
            } else {
                InputParsingResponse.MissingAndRequired
            }
        }
        return InputParsingResponse.MissingAndRequired
    }

    return try {
        InputParsingResponse.Parsed(RESTServerSupport.defaultMapper.readValue<Any>(requestBody, restBody.ref))
    } catch (ex: Exception) {
        when (ex) {
            is IllegalArgumentException, is JsonMappingException, is JsonParseException -> {
                log.debug("Caught exception while trying to deserialize request body")
                log.debug(ex.stackTraceToString())

                InputParsingResponse.MissingAndRequired
            }

            else -> InputParsingResponse.InternalError(ex)
        }
    }
}

class RESTHandler<P : Any, S : Any, E : Any, A : Any>(
    private val payload: P,
    val boundTo: PipelineContext<*, ApplicationCall>,
    val shouldLogResponse: Boolean = false,
    val restCall: RESTCallDescription<P, S, E, A>
) {
    val call: ApplicationCall get() = boundTo.call
    val application: Application get() = boundTo.application

    private var auditRequest: A? = null

    internal var finalized = false

    fun audit(transformedRequest: A) {
        auditRequest = transformedRequest
    }

    suspend fun ok(result: S, status: HttpStatusCode = HttpStatusCode.OK) {
        assert(status.isSuccess())
        finalize(result)

        if (result == Unit) call.respond(status)
        else {
            call.respondText(contentType = ContentType.Application.Json, status = status) {
                defaultMapper.writerFor(restCall.responseTypeSuccess).writeValueAsString(result)
            }
        }
    }

    suspend fun error(error: E, status: HttpStatusCode) {
        assert(!status.isSuccess())
        finalize(error)

        if (error == Unit) call.respond(status)
        else call.respond(status, error)
    }

    /**
     * Signals the implement system that this call is handled externally (by using call.respond)
     *
     * This will trigger logic that is required for finalizing an RPC call. This includes triggering
     * auditing procedures.
     */
    fun okContentDeliveredExternally() {
        finalize(Unit)
    }

    private fun finalize(response: Any) {
        if (shouldLogResponse) {
            call.attributes.put(KafkaHttpRouteLogger.responsePayloadToLogKey, response)
        }

        val auditRequest = if (auditRequest == null) {
            if (restCall.requestType.type == restCall.normalizedRequestTypeForAudit.type) {
                // Safe to just default to the request
                @Suppress("UNCHECKED_CAST")
                payload as A
            } else {
                throw IllegalStateException("Audit request type != request type. You must call audit().")
            }
        } else {
            auditRequest!!
        }

        call.attributes.put(KafkaHttpRouteLogger.requestPayloadToLogKey, auditRequest)

        finalized = true
    }
}

suspend fun RESTHandler<*, Unit, *, *>.ok(status: HttpStatusCode) {
    ok(Unit, status)
}

suspend fun RESTHandler<*, *, Unit, *>.error(status: HttpStatusCode) {
    error(Unit, status)
}

fun <R : Any> RESTPathSegment<R>.bindValuesFromCall(call: ApplicationCall): Pair<String, Any?>? {
    return when (this) {
        is RESTPathSegment.Property<R, *> -> {
            val parameter = call.parameters[property.name]
            @Suppress("UNCHECKED_CAST")
            val companion =
                resolveCompanionInstanceFromType(property.returnType.javaType) as? RequestPathSegmentMarshall<Any>

            if (companion != null) {
                companion.deserializeSegment(this, call)
            } else {
                if (!property.returnType.isMarkedNullable && parameter == null) {
                    throw IllegalArgumentException("Invalid message. Missing parameter '${property.name}'")
                }

                val converted = if (parameter != null) {
                    try {
                        call.application.conversionService.fromValues(listOf(parameter), property.returnType.javaType)
                    } catch (ex: DataConversionException) {
                        throw IllegalArgumentException(ex)
                    } catch (ex: NoSuchElementException) {
                        // For some reason this exception is (incorrectly?) thrown if conversion fails for enums
                        throw IllegalArgumentException(ex)
                    }
                } else {
                    null
                }

                Pair(property.name, converted)
            }
        }

        else -> null
    }
}

fun <R : Any> RESTQueryParameter<R>.bindValuesFromCall(call: ApplicationCall): Pair<String, Any?>? {
    return when (this) {
        is RESTQueryParameter.Property<R, *> -> {
            @Suppress("UNCHECKED_CAST")
            val companion =
                resolveCompanionInstanceFromType(property.returnType.javaType) as? RequestQueryParamMarshall<Any>

            if (companion != null) {
                companion.deserializeQueryParam(this, call)
            } else {
                val parameter = call.request.queryParameters[property.name]
                if (!property.returnType.isMarkedNullable && parameter == null) {
                    throw IllegalArgumentException("Invalid message. Missing parameter '${property.name}'")
                }

                val converted = if (parameter != null) {
                    try {
                        when (property.returnType.classifier) {
                            Boolean::class -> {
                                if (parameter.isEmpty()) true
                                else parameter.toBoolean()
                            }

                            else -> {
                                call.application.conversionService.fromValues(
                                    listOf(parameter),
                                    property.returnType.javaType
                                )
                            }
                        }
                    } catch (ex: DataConversionException) {
                        throw IllegalArgumentException(ex)
                    } catch (ex: NoSuchElementException) {
                        // For some reason this exception is (incorrectly?) thrown if conversion fails for enums
                        throw IllegalArgumentException(ex)
                    }
                } else {
                    null
                }
                Pair(property.name, converted)
            }
        }
    }
}

open class RPCException(val why: String, val httpStatusCode: HttpStatusCode) : RuntimeException(why) {
    companion object {
        fun fromStatusCode(httpStatusCode: HttpStatusCode) = RPCException(httpStatusCode.description, httpStatusCode)
    }
}

package dk.sdu.cloud.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.*
import io.ktor.application.*
import io.ktor.features.conversionService
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.routing.method
import io.ktor.routing.route
import io.ktor.util.DataConversionException
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

private val log = LoggerFactory.getLogger("dk.sdu.cloud.service.ServerSupport")

internal fun Exception.stackTraceToString(): String = StringWriter().apply {
    printStackTrace(PrintWriter(this))
}.toString()

object RESTServerSupport {
    var defaultMapper: ObjectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(JsonParser.Feature.ALLOW_COMMENTS, true)
        configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
    }
    var allowMissingKafkaHttpLogger = false
}

private var didComplainAboutMissingKafkaLogger = false

fun <P : Any, S : Any, E : Any> Route.implement(
    restCall: RESTCallDescription<P, S, E>,
    logPayload: Boolean = true,
    logResponse: Boolean = true,
    payloadTransformerForLog: (P) -> Any = { it },
    handler: suspend RESTHandler<P, S, E>.(P) -> Unit
) {
    val template = restCall.path.toKtorTemplate(fullyQualified = false)
    route(template) {
        method(restCall.method) {
            val logger = application.featureOrNull(KafkaHttpLogger)
            if (logger == null) {
                if (!didComplainAboutMissingKafkaLogger && !RESTServerSupport.allowMissingKafkaHttpLogger) {
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
            } else {
                val fullName = restCall.fullName
                if (fullName != null) {
                    install(KafkaHttpRouteLogger) {
                        requestName = fullName
                    }
                }
            }

            handle {
                val payload: P = if (restCall.requestType == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    Unit as P
                } else {
                    // Parse body as JSON (if any)
                    val valueFromBody =
                        try {
                            parseRequestBody(call.receiveOrNull(), restCall.body).let {
                                when (it) {
                                    is ParsedRequestBody.Parsed -> it.result

                                    ParsedRequestBody.MissingAndNotRequired -> null

                                    ParsedRequestBody.MissingAndRequired -> {
                                        log.debug("Could not parse payload from body, which was required")
                                        return@handle call.respond(HttpStatusCode.BadRequest)
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            when (ex) {
                                is JsonParseException -> {
                                    log.debug("Bad JSON")
                                    log.debug(ex.stackTraceToString())
                                    return@handle call.respond(HttpStatusCode.BadRequest)
                                }

                                else -> {
                                    log.warn("Caught exception while trying to deserialize body!")
                                    log.warn(ex.stackTraceToString())
                                    return@handle call.respond(HttpStatusCode.InternalServerError)
                                }
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
                        restCall.params?.let {
                            it.parameters.mapNotNull { it.bindValuesFromCall(call) }.toMap()
                        }
                    } catch (ex: IllegalArgumentException) {
                        log.debug("Caught illegal argument exception when constructing values from params")
                        log.debug(ex.stackTraceToString())
                        return@handle call.respond(HttpStatusCode.BadRequest)
                    } ?: emptyMap()

                    val allValuesFromRequest = valuesFromPath + valuesFromParams

                    if (restCall.body !is RESTBody.BoundToEntireRequest<*>) {
                        val constructor =
                            restCall.requestType.primaryConstructor ?: restCall.requestType.constructors.single()

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

                if (logPayload) {
                    val transformedPayload = payloadTransformerForLog(payload)
                    call.attributes.put(KafkaHttpRouteLogger.requestPayloadToLogKey, transformedPayload)
                }

                val restHandler = RESTHandler(this, logResponse, restCall)
                try {
                    // Call the handler with the payload
                    restHandler.handler(payload)
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode == HttpStatusCode.InternalServerError) {
                        log.warn("Internal server error in ${restCall.fullName}")
                        log.warn(ex.stackTraceToString())
                    }

                    if (CommonErrorMessage::class == restCall.responseTypeFailure) {
                        val message =
                            if (ex.httpStatusCode != HttpStatusCode.InternalServerError) ex.why else "Internal Server Error"

                        @Suppress("UNCHECKED_CAST")
                        restHandler.error(CommonErrorMessage(message) as E, ex.httpStatusCode)
                    } else {
                        log.info("Cannot auto-complete exception message when error type is not " +
                                "CommonErrorMessage. Please catch exceptions yourself.")
                        call.respond(ex.httpStatusCode)
                    }
                }
            }
        }
    }
}

private sealed class ParsedRequestBody {
    data class Parsed(val result: Any) : ParsedRequestBody()
    object MissingAndRequired : ParsedRequestBody()
    object MissingAndNotRequired : ParsedRequestBody()
}

private fun parseRequestBody(requestBody: String?, restBody: RESTBody<*, *>?): ParsedRequestBody {
    // We silently ignore a body which is not required
    if (restBody == null) return ParsedRequestBody.MissingAndNotRequired

    val hasText = !requestBody.isNullOrEmpty()
    if (!hasText) {
        if (restBody is RESTBody.BoundToSubProperty) {
            return if (restBody.property.returnType.isMarkedNullable) {
                ParsedRequestBody.MissingAndNotRequired
            } else {
                ParsedRequestBody.MissingAndRequired
            }
        }
        return ParsedRequestBody.MissingAndRequired
    }

    return try {
        ParsedRequestBody.Parsed(RESTServerSupport.defaultMapper.readValue<Any>(requestBody, restBody.ref))
    } catch (ex: Exception) {
        when (ex) {
            is IllegalArgumentException, is JsonMappingException -> {
                log.debug("Caught exception while trying to deserialize request body")
                log.debug(ex.stackTraceToString())

                ParsedRequestBody.MissingAndRequired
            }

            else -> throw ex
        }
    }
}

class RESTHandler<P : Any, S : Any, E : Any>(
    val boundTo: PipelineContext<*, ApplicationCall>,
    val shouldLogResponse: Boolean = true,
    val restCall: RESTCallDescription<P, S, E>
) {
    val call: ApplicationCall get() = boundTo.call
    val application: Application get() = boundTo.application

    suspend fun ok(result: S, status: HttpStatusCode = HttpStatusCode.OK) {
        assert(status.value in 200..299)
        if (shouldLogResponse) {
            call.attributes.put(KafkaHttpRouteLogger.responsePayloadToLogKey, result)
        }

        if (result == Unit) call.respond(status)
        else call.respond(status, result)
    }

    suspend fun error(error: E, status: HttpStatusCode) {
        assert(status.value !in 200..299)
        if (shouldLogResponse) {
            call.attributes.put(KafkaHttpRouteLogger.responsePayloadToLogKey, error)
        }

        if (error == Unit) call.respond(status)
        else call.respond(status, error)
    }
}

suspend fun RESTHandler<*, Unit, *>.ok(status: HttpStatusCode) {
    ok(Unit, status)
}

suspend fun RESTHandler<*, *, Unit>.error(status: HttpStatusCode) {
    error(Unit, status)
}

fun <R : Any> RESTPathSegment<R>.bindValuesFromCall(call: ApplicationCall): Pair<String, Any?>? {
    return when (this) {
        is RESTPathSegment.Property<R, *> -> {
            val parameter = call.parameters[property.name]
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

        else -> null
    }
}

fun <R : Any> RESTQueryParameter<R>.bindValuesFromCall(call: ApplicationCall): Pair<String, Any?>? {
    return when (this) {
        is RESTQueryParameter.Property<R, *> -> {
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

open class RPCException(val why: String, val httpStatusCode: HttpStatusCode) : RuntimeException(why)

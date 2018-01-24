package dk.sdu.cloud.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.client.*
import io.ktor.application.*
import io.ktor.features.conversionService
import io.ktor.http.HttpMethod
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

// TODO We should find a better solution for all of these "defaultMappers"
object RESTServerSupport {
    var defaultMapper: ObjectMapper = jacksonObjectMapper()
}

private var didComplainAboutMissingKafkaLogger = false

fun <P : Any, S : Any, E : Any> Route.implement(
    restCall: RESTCallDescription<P, S, E>,
    handler: suspend RESTHandler<P, S, E>.(P) -> Unit
) {
    val template = restCall.path.toKtorTemplate(fullyQualified = false)
    route(template) {
        method(HttpMethod.parse(restCall.method.name())) {
            val logger = application.featureOrNull(KafkaHttpLogger)
            if (logger == null) {
                if (!didComplainAboutMissingKafkaLogger) {
                    log.warn("implement() calls require the KafkaHttpLogger feature to be installed!")
                    log.warn("implement() calls require the KafkaHttpLogger feature to be installed!")
                    log.warn("implement() calls require the KafkaHttpLogger feature to be installed!")
                    log.warn("NO Kafka logging will be performed without this feature present. The implement " +
                            "call was placed here:")
                    try {
                        throw RuntimeException()
                    } catch (ex: RuntimeException) {
                        log.warn(ex.stackTraceToString())
                    }

                    log.warn("We will not complain about further violations.")
                    didComplainAboutMissingKafkaLogger = true
                }
            } else {
                val stream = restCall.loggingStream()
                if (stream != null) {
                    val kafkaProducer = logger.kafka.producer.forStream(stream)
                    install(KafkaHttpRouteLogger) {
                        producer = kafkaProducer
                    }
                }
            }
            handle {
                val payload: P = if (restCall.requestType == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    Unit as P
                } else {
                    // Parse body as JSON (if any)
                    val valueFromBody = parseRequestBody(call.receiveOrNull(), restCall.body).let {
                        when (it) {
                            is ParsedRequestBody.Parsed -> it.result

                            ParsedRequestBody.MissingAndNotRequired -> null

                            ParsedRequestBody.MissingAndRequired -> {
                                return@handle call.respond(HttpStatusCode.BadRequest)
                            }
                        }
                    }

                    // Retrieve argument values from path (if any)
                    val valuesFromPath = try {
                        restCall.path.segments.mapNotNull { it.bindValuesFromCall(call) }.toMap()
                    } catch (ex: IllegalArgumentException) {
                        return@handle call.respond(HttpStatusCode.BadRequest)
                    }

                    // Retrieve arguments from query parameters (if any)
                    val valuesFromParams = try {
                        restCall.params?.let {
                            it.parameters.mapNotNull { it.bindValuesFromCall(call) }.toMap()
                        }
                    } catch (ex: IllegalArgumentException) {
                        return@handle call.respond(HttpStatusCode.BadRequest)
                    } ?: emptyMap()

                    val allValuesFromRequest = valuesFromPath + valuesFromParams

                    // TODO We need to handle this case for params too. That is, if the entire thing is bound
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

                // Call the handler with the payload
                RESTHandler<P, S, E>(this).handler(payload)
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
        // TODO Don't assume that all exceptions are user input errors
        log.debug("Caught exception while trying to deserialize request body")
        log.debug(ex.stackTraceToString())

        ParsedRequestBody.MissingAndRequired
    }
}

class RESTHandler<P : Any, in S : Any, in E : Any>(val boundTo: PipelineContext<*, ApplicationCall>) {
    val call: ApplicationCall get() = boundTo.call
    val application: Application get() = boundTo.application

    suspend fun ok(result: S, status: HttpStatusCode = HttpStatusCode.OK) {
        assert(status.value in 200..299)
        call.respond(status, result)
    }

    suspend fun error(error: E, status: HttpStatusCode) {
        assert(status.value !in 200..299)
        call.respond(status, error)
    }
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
}

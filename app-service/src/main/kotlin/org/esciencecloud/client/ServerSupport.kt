package org.esciencecloud.client

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.features.conversionService
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.method
import io.ktor.routing.route
import io.ktor.util.DataConversionException
import org.slf4j.LoggerFactory
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

private val log = LoggerFactory.getLogger("org.esciencecloud.client.ServerSupport")

fun <P : Any, S : Any, E : Any> Route.implement(
        restCall: RESTCallDescription<P, S, E>,
        handler: suspend RESTHandler<P, S, E>.(P) -> Unit
) {
    val template = restCall.path.segments.joinToString("/") { it.toKtorTemplateString() }
    println(template)
    route(template) {
        method(restCall.method) {
            handle {
                val payload: P = if (restCall.requestType == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    Unit as P
                } else {
                    val arguments = try {
                        restCall.path.segments.mapNotNull {
                            it.bindValuesFromCall(call)
                        }.toMap()
                    } catch (ex: IllegalArgumentException) {
                        return@handle call.respond(HttpStatusCode.BadRequest)
                    }

                    val constructor = restCall.requestType.primaryConstructor ?: restCall.requestType.constructors.single()
                    val resolvedArguments = constructor.parameters.map {
                        val name = it.name ?: throw IllegalStateException("Unable to determine name of property in " +
                                "request type. Please use a data class instead to solve this problem.")

                        if (name !in arguments) {
                            throw IllegalStateException("The property '$name' was not bound in description!")
                        }

                        it to arguments[name]
                    }.toMap()
                    constructor.callBy(resolvedArguments)
                }

                RESTHandler<P, S, E>(this).handler(payload)
            }
        }
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

// We should keep these out, such that they are in a module which is only used by service definitions (to not
// force ktor dependency on clients)

fun <R : Any> RESTPathSegment<R>.toKtorTemplateString(): String = when (this) {
    is RESTPathSegment.Simple -> text

    is RESTPathSegment.Property<R, *> -> StringBuilder().apply {
        append('{')
        append(property.name)
        if (property.returnType.isMarkedNullable) append('?')
        append('}')
    }.toString()

    is RESTPathSegment.Remaining -> "{...}"
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

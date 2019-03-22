package dk.sdu.cloud.calls.server

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

object RequestParsing : Loggable {
    override val log = logger()

    fun <P : Any> constructFromAttributes(target: Type, parameters: Map<String, Any?>): P {
        val requestClass = target as? Class<*> ?: throw IllegalStateException(
            "$target is not a simple class. This is required."
        )

        val requestClassKotlin = if (requestClass.isKotlinClass()) {
            @Suppress("UNCHECKED_CAST")
            requestClass.kotlin as KClass<P>
        } else {
            throw IllegalStateException(
                "$target request type is not a kotlin class. This is required."
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

            if (name !in parameters) {
                throw IllegalStateException("The property '$name' of $target was not bound in description!")
            }

            it to parameters[name]
        }.toMap()

        try {
            return constructor.callBy(resolvedArguments)
        } catch (ex: Exception) {
            when (ex) {
                is RPCException -> throw ex

                is IllegalArgumentException -> {
                    log.info("Request invalidation should throw an RPCException instead of an IllegalArgumentException")
                    log.info("No error message will be attached!")

                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }

                else -> {
                    log.warn("Caught exception during construction of request object!")
                    log.warn(ex.stackTraceToString())
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

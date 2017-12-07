package org.esciencecloud.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import org.asynchttpclient.BoundRequestBuilder
import org.slf4j.LoggerFactory

abstract class RESTDescriptions {
    private val log = LoggerFactory.getLogger(javaClass)

    private val _descriptions: MutableList<String> = ArrayList()
    val descriptions: List<String> get() = _descriptions.toList()

    /**
     * Creates a [RESTCallDescription] and registers it in the container.
     *
     * To do this manually create a description and call [register] with the template.
     */
    inline fun <reified R : Any, reified S : Any, reified E : Any> callDescription(
            mapper: ObjectMapper = HttpClient.defaultMapper,
            noinline additionalRequestConfiguration: (BoundRequestBuilder.(R) -> Unit)? = null,
            body: RESTCallDescriptionBuilder<R, S, E>.() -> Unit
    ): RESTCallDescription<R, S, E> {
        val builder = RESTCallDescriptionBuilder<R, S, E>(
                requestType = R::class,
                deserializerSuccess = mapper.readerFor(jacksonTypeRef<S>()),
                deserializerError = mapper.readerFor(jacksonTypeRef<E>())
        )
        builder.body()
        val result = builder.build(additionalRequestConfiguration)
        register(result.path.toKtorTemplate(fullyQualified = true))
        return result
    }

    /**
     * Utility function for creating KafkaDescriptions.
     *
     * This is the same as using [callDescription] with no registration at GW at with predefined [GatewayJobResponse]
     * for both success and error messages.
     */
    inline fun <reified R : Any> kafkaDescription(
            mapper: ObjectMapper = HttpClient.defaultMapper,
            noinline additionalRequestConfiguration: (BoundRequestBuilder.(R) -> Unit)? = null,
            body: RESTCallDescriptionBuilder<R, GatewayJobResponse, GatewayJobResponse>.() -> Unit
    ): KafkaCallDescription<R> = callDescription(mapper, additionalRequestConfiguration) {
        // Placed before call to body() to allow these to be overwritten
        shouldProxyFromGateway = false

        body()
    }

    /**
     * Registers a ktor style template in this container.
     */
    fun register(template: String) {
        log.debug("Registering new ktor template: $template")
        _descriptions.add(template)
    }
}
fun RESTPath<*>.toKtorTemplate(fullyQualified: Boolean = false): String {
    val primaryPart = segments.joinToString("/") { it.toKtorTemplateString() }
    return if (fullyQualified) {
        basePath.removeSuffix("/") + "/" + primaryPart
    } else {
        primaryPart
    }
}

private fun <R : Any> RESTPathSegment<R>.toKtorTemplateString(): String = when (this) {
    is RESTPathSegment.Simple -> text

    is RESTPathSegment.Property<R, *> -> StringBuilder().apply {
        append('{')
        append(property.name)
        if (property.returnType.isMarkedNullable) append('?')
        append('}')
    }.toString()

    is RESTPathSegment.Remaining -> "{...}"
}

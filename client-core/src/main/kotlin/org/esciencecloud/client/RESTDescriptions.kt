package org.esciencecloud.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.netty.handler.codec.http.HttpMethod
import org.asynchttpclient.BoundRequestBuilder
import org.slf4j.LoggerFactory

sealed class ProxyDescription {
    abstract val template: String
    abstract val method: HttpMethod
    abstract val shouldProxyFromGateway: Boolean

    data class Manual(
            override val template: String,
            override val method: HttpMethod,
            override val shouldProxyFromGateway: Boolean
    ) : ProxyDescription()

    data class FromDescription(
            val description: RESTCallDescription<*, *, *>
    ) : ProxyDescription() {
        override val method: HttpMethod
            get() = description.method

        override val shouldProxyFromGateway: Boolean
            get() = description.shouldProxyFromGateway

        override val template: String
            get() = description.path.toKtorTemplate(fullyQualified = true)
    }
}

abstract class RESTDescriptions {
    private val log = LoggerFactory.getLogger(javaClass)

    private val _descriptions: MutableList<ProxyDescription> = ArrayList()
    val descriptions: List<ProxyDescription> get() = _descriptions.toList()

    /**
     * Creates a [RESTCallDescription] and registers it in the container.
     *
     * To do this manually create a description and call [register] with the template.
     */
    inline protected fun <reified R : Any, reified S : Any, reified E : Any> callDescription(
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
        return builder.build(additionalRequestConfiguration)
    }

    /**
     * Utility function for creating KafkaDescriptions.
     *
     * This is the same as using [callDescription] with no registration at GW at with predefined [GatewayJobResponse]
     * for both success and error messages.
     */
    inline protected fun <reified R : Any> kafkaDescription(
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
    protected fun register(template: String, method: HttpMethod) {
        log.debug("Registering new ktor template: $template")
        _descriptions.add(ProxyDescription.Manual(template, method, true))
    }

    protected fun register(description: RESTCallDescription<*, *, *>) {
        log.debug("Registering new ktor template: ${description.path.toKtorTemplate(true)}")
        _descriptions.add(ProxyDescription.FromDescription(description))
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

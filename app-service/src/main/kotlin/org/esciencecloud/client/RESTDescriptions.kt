package org.esciencecloud.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import org.esciencecloud.asynchttp.HttpClient
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
            body: RESTCallDescriptionBuilder<R, S, E>.() -> Unit
    ): RESTCallDescription<R, S, E> {
        val builder = RESTCallDescriptionBuilder<R, S, E>(
                requestType = R::class,
                deserializerSuccess = mapper.readerFor(jacksonTypeRef<S>()),
                deserializerError = mapper.readerFor(jacksonTypeRef<E>())
        )
        builder.body()
        val result = builder.build()
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
            body: RESTCallDescriptionBuilder<R, GatewayJobResponse, GatewayJobResponse>.() -> Unit
    ): KafkaCallDescription<R> = callDescription(mapper, body)

    /**
     * Registers a ktor style template in this container.
     */
    fun register(template: String) {
        log.debug("Registering new ktor template: $template")
        _descriptions.add(template)
    }
}
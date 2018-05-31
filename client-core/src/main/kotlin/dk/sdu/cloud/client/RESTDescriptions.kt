package dk.sdu.cloud.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpMethod
import org.slf4j.LoggerFactory

sealed class ProxyDescription {
    abstract val template: String
    abstract val method: HttpMethod

    data class Manual(
        override val template: String,
        override val method: HttpMethod
    ) : ProxyDescription()

    data class FromDescription(
        val description: RESTCallDescription<*, *, *>
    ) : ProxyDescription() {
        override val method: HttpMethod
            get() = description.method

        override val template: String
            get() = description.path.toKtorTemplate(fullyQualified = true)
    }
}


abstract class RESTDescriptions(val owner: ServiceDescription) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val _descriptions: MutableList<ProxyDescription> = ArrayList()
    val descriptions: List<ProxyDescription> get() = _descriptions.toList()

    /**
     * Creates a [RESTCallDescription] and registers it in the container.
     *
     * To do this manually create a description and call [register] with the template.
     */
    protected inline fun <reified R : Any, reified S : Any, reified E : Any> callDescription(
        mapper: ObjectMapper = defaultMapper,
        noinline additionalRequestConfiguration: (HttpRequestBuilder.(R) -> Unit)? = null,
        body: RESTCallDescriptionBuilder<R, S, E>.() -> Unit
    ): RESTCallDescription<R, S, E> {
        val builder = RESTCallDescriptionBuilder(
            requestType = R::class,
            responseTypeSuccess = S::class,
            responseTypeFailure = E::class,
            deserializerSuccess = mapper.readerFor(jacksonTypeRef<S>()),
            deserializerError = mapper.readerFor(jacksonTypeRef<E>())
        )
        builder.body()
        return builder.build(owner, additionalRequestConfiguration).also { register(it) }
    }

    /**
     * Registers a ktor style template in this container.
     */
    protected fun register(template: String, method: HttpMethod) {
        log.info("Registering new ktor template: $template")
        _descriptions.add(ProxyDescription.Manual(template, method))
    }

    protected fun register(description: RESTCallDescription<*, *, *>) {
        log.info("Registering new ktor template: ${description.path.toKtorTemplate(true)}")
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

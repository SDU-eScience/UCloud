package dk.sdu.cloud.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import io.ktor.client.request.HttpRequestBuilder
import org.slf4j.LoggerFactory

abstract class RESTDescriptions(val namespace: String) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val _descriptions: MutableList<RESTCallDescription<*, *, *, *>> = ArrayList()
    val descriptions: List<RESTCallDescription<*, *, *, *>> get() = _descriptions.toList()

    /**
     * Creates a [RESTCallDescription] and registers it in the container.
     */
    protected inline fun <reified Request : Any, reified Success : Any, reified Error : Any> callDescription(
        mapper: ObjectMapper = defaultMapper,
        noinline additionalRequestConfiguration: (HttpRequestBuilder.(Request) -> Unit)? = null,
        body: RESTCallDescriptionBuilder<Request, Success, Error, Request>.() -> Unit
    ): RESTCallDescription<Request, Success, Error, Request> {
        return callDescriptionWithAudit(mapper, additionalRequestConfiguration, body)
    }

    /**
     * Creates a [RESTCallDescription] and registers it in the container.
     */
    protected inline fun <
            reified Request : Any,
            reified Success : Any,
            reified Error : Any,
            reified AuditEntry : Any> callDescriptionWithAudit(
        mapper: ObjectMapper = defaultMapper,
        noinline additionalRequestConfiguration: (HttpRequestBuilder.(Request) -> Unit)? = null,
        body: RESTCallDescriptionBuilder<Request, Success, Error, AuditEntry>.() -> Unit
    ): RESTCallDescription<Request, Success, Error, AuditEntry> {
        val builder = RESTCallDescriptionBuilder(
            requestType = jacksonTypeRef<Request>(),
            responseTypeSuccess = jacksonTypeRef<Success>(),
            responseTypeFailure = jacksonTypeRef<Error>(),
            normalizedRequestTypeForAudit = jacksonTypeRef<AuditEntry>(),
            deserializerSuccess = mapper.readerFor(jacksonTypeRef<Success>()),
            deserializerError = mapper.readerFor(jacksonTypeRef<Error>())
        )
        builder.body()
        return builder.build(namespace, additionalRequestConfiguration).also { register(it) }
    }

    @PublishedApi
    internal fun register(description: RESTCallDescription<*, *, *, *>) {
        log.info("Registering new ktor template: ${description.path.toKtorTemplate(true)}")
        _descriptions.add(description)
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

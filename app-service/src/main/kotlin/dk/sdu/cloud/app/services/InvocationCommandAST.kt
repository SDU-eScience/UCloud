package dk.sdu.cloud.app.services

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.util.BashEscaper
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(InvocationParameter::class.java)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = WordInvocationParameter::class, name = "word"),
    JsonSubTypes.Type(value = VariableInvocationParameter::class, name = "var")
)
sealed class InvocationParameter {
    abstract fun buildInvocationSnippet(parameters: Map<ApplicationParameter<*>, Any>): String
}

class WordInvocationParameter(val word: String) : InvocationParameter() {
    override fun buildInvocationSnippet(parameters: Map<ApplicationParameter<*>, Any>): String {
        return word
    }
}

class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String = "",
    val suffixGlobal: String = "",
    val prefixVariable: String = "",
    val suffixVariable: String = "",
    val variableSeparator: String = " "
) : InvocationParameter() {
    override fun buildInvocationSnippet(parameters: Map<ApplicationParameter<*>, Any>): String {
        val relevantTypesToValue = parameters.filter { it.key.name in variableNames }

        if (relevantTypesToValue.size != variableNames.size) {
            val notFound = parameters.filter { it.key.name !in variableNames }.map { it.key.name }
            log.warn("Could not find the following parameters: $notFound")
        }

        val middlePart = relevantTypesToValue.map {
            @Suppress("UNCHECKED_CAST")
            val parameter = it.key as ApplicationParameter<Any>

            prefixVariable +
                    BashEscaper.safeBashArgument(parameter.toInvocationArgument(it.value)) +
                    suffixVariable
        }.joinToString(variableSeparator)

        return prefixGlobal + middlePart + suffixGlobal
    }
}

fun Iterable<InvocationParameter>.buildSafeBashString(parameters: Map<ApplicationParameter<*>, Any>): String =
    joinToString(" ") { it.buildInvocationSnippet(parameters) }

package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.stackTraceToString
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(InvocationParameter::class.java)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = WordInvocationParameter::class, name = "word"),
    JsonSubTypes.Type(value = BooleanFlagParameter::class, name = "bool_flag"),
    JsonSubTypes.Type(value = VariableInvocationParameter::class, name = "var")
)
sealed class InvocationParameter {
    abstract fun buildInvocationSnippet(parameters: Map<ApplicationParameter<*>, Any?>): String?
}

class WordInvocationParameter(val word: String) : InvocationParameter() {
    override fun buildInvocationSnippet(parameters: Map<ApplicationParameter<*>, Any?>): String? {
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
    override fun buildInvocationSnippet(parameters: Map<ApplicationParameter<*>, Any?>): String? {
        val relevantTypesToValue = parameters.filter { it.key.name in variableNames }
        val nameToTypeAndValue = relevantTypesToValue.entries.associateBy { it.key.name }

        if (relevantTypesToValue.size != variableNames.size) {
            val notFound = parameters.filter { it.key.name !in variableNames }.map { it.key.name }
            log.warn("Could not find the following parameters: $notFound")
        }

        val middlePart = variableNames.mapNotNull {
            val typeAndValue = nameToTypeAndValue[it] ?: return@mapNotNull null
            val value = typeAndValue.value ?: return@mapNotNull null

            @Suppress("UNCHECKED_CAST")
            val parameter = typeAndValue.key as ApplicationParameter<Any>

            prefixVariable +
                    BashEscaper.safeBashArgument(parameter.toInvocationArgument(value)) +
                    suffixVariable
        }.joinToString(variableSeparator)

        return prefixGlobal + middlePart + suffixGlobal
    }
}

class BooleanFlagParameter(
    val variableName: String,
    val flag: String
) : InvocationParameter() {
    override fun buildInvocationSnippet(parameters: Map<ApplicationParameter<*>, Any?>): String? {
        val parameter = parameters.filterKeys { it.name == variableName }.keys.singleOrNull()
                ?: return null

        val value = parameters[parameter] as? Boolean ?: throw InvalidParamUsage(
            "Invalid type",
            this,
            parameters
        )

        return if (value) flag else null
    }
}

private data class InvalidParamUsage(
    val why: String,
    val param: InvocationParameter,
    val parameters: Map<ApplicationParameter<*>, Any?>
) : RuntimeException(why) {
    override fun toString(): String {
        return "InvalidParamUsage(why='$why', param=$param, parameters=$parameters)"
    }
}

fun Iterable<InvocationParameter>.buildSafeBashString(parameters: Map<ApplicationParameter<*>, Any>): String =
    mapNotNull {
        try {
            it.buildInvocationSnippet(parameters)
        } catch (ex: InvalidParamUsage) {
            log.warn(ex.stackTraceToString())
            null
        }
    }.joinToString(" ")

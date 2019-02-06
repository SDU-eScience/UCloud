package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.BashEscaper
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
    abstract fun buildInvocationSnippet(parameters: AppParametersWithValues): String?
}

data class WordInvocationParameter(val word: String) : InvocationParameter() {
    override fun buildInvocationSnippet(parameters: AppParametersWithValues): String? {
        return word
    }
}

typealias AppParametersWithValues = Map<ApplicationParameter<*>, ParsedApplicationParameter?>

data class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String = "",
    val suffixGlobal: String = "",
    val prefixVariable: String = "",
    val suffixVariable: String = "",
    val variableSeparator: String = " "
) : InvocationParameter() {
    override fun buildInvocationSnippet(parameters: AppParametersWithValues): String? {
        val relevantTypesToValue = parameters.filter { it.key.name in variableNames }
        val nameToTypeAndValue = relevantTypesToValue.entries.associateBy { it.key.name }

        if (relevantTypesToValue.size != variableNames.size) {
            val paramNames = parameters.map { it.key.name }.toSet()
            val notFound = variableNames.filter { it !in paramNames }
            log.warn("Could not find the following parameters: $notFound")
        }

        val middlePart = variableNames.asSequence().mapNotNull {
            val typeAndValue = nameToTypeAndValue[it] ?: return@mapNotNull null
            val value = typeAndValue.value ?: return@mapNotNull null

            @Suppress("UNCHECKED_CAST")
            val parameter = typeAndValue.key as ApplicationParameter<ParsedApplicationParameter>

            prefixVariable +
                    BashEscaper.safeBashArgument(parameter.toInvocationArgument(value)) +
                    suffixVariable
        }.joinToString(variableSeparator)

        return prefixGlobal + middlePart + suffixGlobal
    }
}

data class BooleanFlagParameter(
    val variableName: String,
    val flag: String
) : InvocationParameter() {
    override fun buildInvocationSnippet(parameters: AppParametersWithValues): String? {
        val parameter = parameters.filterKeys { it.name == variableName }.keys.singleOrNull()
            ?: return null

        val value = parameters[parameter] as? BooleanApplicationParameter ?: throw InvalidParamUsage(
            "Invalid type",
            this,
            parameters
        )

        return if (value.value) flag else null
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

fun Iterable<InvocationParameter>.buildSafeBashString(parameters: AppParametersWithValues): String =
    mapNotNull {
        try {
            it.buildInvocationSnippet(parameters)
        } catch (ex: InvalidParamUsage) {
            log.warn(ex.stackTraceToString())
            null
        }
    }.joinToString(" ")

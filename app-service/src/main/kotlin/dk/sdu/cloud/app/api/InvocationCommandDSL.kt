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
    JsonSubTypes.Type(value = VariableInvocationParameter::class, name = "var"),
    JsonSubTypes.Type(value = EnvironmentVariableParameter::class, name = "env")
)
sealed class InvocationParameter {
    abstract fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext = InvocationParameterContext.COMMAND
    ): List<String>
}

enum class InvocationParameterContext {
    COMMAND,
    ENVIRONMENT
}

fun InvocationParameter.buildInvocationSnippet(parameters: AppParametersWithValues): String? {
    return buildInvocationList(parameters, InvocationParameterContext.COMMAND)
        .takeIf { it.isNotEmpty() }?.joinToString(" ") { BashEscaper.safeBashArgument(it) }
}

fun InvocationParameter.buildEnvironmentValue(parameters: AppParametersWithValues): String? {
    return buildInvocationList(parameters, InvocationParameterContext.ENVIRONMENT).takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
}

data class EnvironmentVariableParameter(val variable: String) : InvocationParameter() {
    override fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext
    ): List<String> {
        if (context != InvocationParameterContext.ENVIRONMENT) return emptyList()
        return listOf("$($variable)")
    }
}

data class WordInvocationParameter(val word: String) : InvocationParameter() {
    override fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext
    ): List<String> {
        return listOf(word)
    }
}

typealias AppParametersWithValues = Map<ApplicationParameter<*>, ParsedApplicationParameter?>

data class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String = "",
    val suffixGlobal: String = "",
    val prefixVariable: String = "",
    val suffixVariable: String = "",
    val isPrefixVariablePartOfArg: Boolean = false,
    val isSuffixVariablePartOfArg: Boolean = false
) : InvocationParameter() {
    override fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext
    ): List<String> {
        val prefixGlobal = this.prefixGlobal.trim()
        val suffixGlobal = this.suffixGlobal.trim()
        val prefixVariable = this.prefixVariable.trim()
        val suffixVariable = this.suffixVariable.trim()

        val fieldToValue = parameters.filter { it.key.name in variableNames }
        val nameToFieldAndValue = fieldToValue.entries.associateBy { it.key.name }

        // We assume that verification has already taken place. If we have no values it should mean that they are all
        // optional. We don't include anything (including prefixGlobal) if no values were given.
        if (fieldToValue.isEmpty()) {
            return emptyList()
        }

        val middlePart = variableNames.flatMap {
            val fieldAndValue = nameToFieldAndValue[it] ?: return@flatMap emptyList<String>()
            val value = fieldAndValue.value ?: return@flatMap emptyList<String>()

            @Suppress("UNCHECKED_CAST")
            val parameter = fieldAndValue.key as ApplicationParameter<ParsedApplicationParameter>

            val args = ArrayList<String>()
            val mainArg = StringBuilder()
            if (isPrefixVariablePartOfArg) {
                mainArg.append(prefixVariable)
            } else {
                if (prefixVariable.isNotBlank()) {
                    args.add(prefixVariable)
                }
            }

            mainArg.append(parameter.toInvocationArgument(value))

            if (isSuffixVariablePartOfArg) {
                mainArg.append(suffixVariable)
                args.add(mainArg.toString())
            } else {
                args.add(mainArg.toString())
                if (suffixVariable.isNotBlank()) {
                    args.add(suffixVariable)
                }
            }

            args
        }

        return run {
            val result = ArrayList<String>()
            if (prefixGlobal.isNotBlank()) {
                result.add(prefixGlobal)
            }

            result.addAll(middlePart)

            if (suffixGlobal.isNotBlank()) {
                result.add(suffixGlobal)
            }

            result
        }

    }
}

data class BooleanFlagParameter(
    val variableName: String,
    val flag: String
) : InvocationParameter() {
    override fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext
    ): List<String> {
        val parameter = parameters.filterKeys { it.name == variableName }.keys.singleOrNull()
            ?: return emptyList()

        val value = parameters[parameter] as? BooleanApplicationParameter ?: throw InvalidParamUsage(
            "Invalid type",
            this,
            parameters
        )

        return if (value.value) listOf(flag.trim()) else emptyList()
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

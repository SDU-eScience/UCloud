package dk.sdu.cloud.app.store.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.BashEscaper
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
        context: InvocationParameterContext = InvocationParameterContext.COMMAND,
        builder: ArgumentBuilder = ArgumentBuilder.Default,
    ): List<String>
}

enum class InvocationParameterContext {
    COMMAND,
    ENVIRONMENT
}

interface ArgumentBuilder {
    fun build(parameter: ApplicationParameter, value: AppParameterValue): String

    companion object Default : ArgumentBuilder {
        override fun build(parameter: ApplicationParameter, value: AppParameterValue): String {
            when (parameter) {
                is ApplicationParameter.InputFile, is ApplicationParameter.InputDirectory -> {
                    return (value as AppParameterValue.File).path
                }

                is ApplicationParameter.Text -> {
                    return (value as AppParameterValue.Text).value
                }

                is ApplicationParameter.Integer -> {
                    return (value as AppParameterValue.Integer).value.toString()
                }

                is ApplicationParameter.FloatingPoint -> {
                    return (value as AppParameterValue.FloatingPoint).value.toString()
                }

                is ApplicationParameter.Bool -> {
                    return (value as AppParameterValue.Bool).value.toString()
                }

                is ApplicationParameter.Enumeration -> {
                    val inputValue = (value as AppParameterValue.Text).value
                    val option = parameter.options.find { it.name == inputValue }
                    return option?.value ?: inputValue
                }

                is ApplicationParameter.Peer -> {
                    return (value as AppParameterValue.Peer).hostname
                }

                is ApplicationParameter.LicenseServer -> {
                    val license = (value as AppParameterValue.License)
                    return buildString {
                        append(license.address)
                        append(":")
                        append(license.port)
                        if (license.license != null) {
                            append("/")
                            append(license.license)
                        }
                    }
                }
            }
        }
    }
}

fun InvocationParameter.buildInvocationSnippet(
    parameters: AppParametersWithValues,
    builder: ArgumentBuilder = ArgumentBuilder.Default,
): String? {
    return buildInvocationList(parameters, InvocationParameterContext.COMMAND, builder)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { BashEscaper.safeBashArgument(it) }
}

fun InvocationParameter.buildEnvironmentValue(
    parameters: AppParametersWithValues,
    builder: ArgumentBuilder = ArgumentBuilder.Default,
): String? {
    return buildInvocationList(parameters, InvocationParameterContext.ENVIRONMENT, builder)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
}

data class EnvironmentVariableParameter(val variable: String) : InvocationParameter() {
    override fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext,
        builder: ArgumentBuilder,
    ): List<String> {
        if (context != InvocationParameterContext.ENVIRONMENT) return emptyList()
        return listOf("$($variable)")
    }
}

data class WordInvocationParameter(val word: String) : InvocationParameter() {
    override fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext,
        builder: ArgumentBuilder,
    ): List<String> {
        return listOf(word)
    }
}

typealias AppParametersWithValues = Map<ApplicationParameter, AppParameterValue?>

data class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String = "",
    val suffixGlobal: String = "",
    val prefixVariable: String = "",
    val suffixVariable: String = "",
    val isPrefixVariablePartOfArg: Boolean = false,
    val isSuffixVariablePartOfArg: Boolean = false,
) : InvocationParameter() {
    override fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext,
        builder: ArgumentBuilder,
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
            val parameter = fieldAndValue.key

            val args = ArrayList<String>()
            val mainArg = StringBuilder()
            if (isPrefixVariablePartOfArg) {
                mainArg.append(prefixVariable)
            } else {
                if (prefixVariable.isNotBlank()) {
                    args.add(prefixVariable)
                }
            }

            mainArg.append(builder.build(parameter, value))

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
    val flag: String,
) : InvocationParameter() {
    override fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext,
        builder: ArgumentBuilder,
    ): List<String> {
        val parameter = parameters.filterKeys { it.name == variableName }.keys.singleOrNull()
            ?: return emptyList()

        val value = parameters[parameter] as? AppParameterValue.Bool ?: throw InvalidParamUsage(
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
    val parameters: Map<ApplicationParameter, Any?>,
) : RuntimeException(why) {
    override fun toString(): String {
        return "InvalidParamUsage(why='$why', param=$param, parameters=$parameters)"
    }
}

fun Iterable<InvocationParameter>.buildSafeBashString(
    parameters: AppParametersWithValues,
    builder: ArgumentBuilder,
): String =
    mapNotNull {
        try {
            it.buildInvocationSnippet(parameters, builder)
        } catch (ex: InvalidParamUsage) {
            log.warn(ex.stackTraceToString())
            null
        }
    }.joinToString(" ")

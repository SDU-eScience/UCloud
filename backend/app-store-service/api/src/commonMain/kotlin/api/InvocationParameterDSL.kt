package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.service.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val log = Logger("InvocationParameter")

typealias AppParametersWithValues = Map<ApplicationParameter, AppParameterValue?>

@Serializable
sealed class InvocationParameter {
    abstract suspend fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext = InvocationParameterContext.COMMAND,
        builder: ArgumentBuilder = ArgumentBuilder.Default,
    ): List<String>
}

@Serializable
enum class InvocationParameterContext {
    COMMAND,
    ENVIRONMENT
}

@Serializable
@SerialName("env")
data class EnvironmentVariableParameter(val variable: String) : InvocationParameter() {
    override suspend fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext,
        builder: ArgumentBuilder,
    ): List<String> {
        if (context != InvocationParameterContext.ENVIRONMENT) return emptyList()
        return listOf("$($variable)")
    }
}

@Serializable
@SerialName("word")
data class WordInvocationParameter(val word: String) : InvocationParameter() {
    override suspend fun buildInvocationList(
        parameters: AppParametersWithValues,
        context: InvocationParameterContext,
        builder: ArgumentBuilder,
    ): List<String> {
        return listOf(word)
    }
}

@Serializable
@SerialName("var")
data class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String = "",
    val suffixGlobal: String = "",
    val prefixVariable: String = "",
    val suffixVariable: String = "",
    val isPrefixVariablePartOfArg: Boolean = false,
    val isSuffixVariablePartOfArg: Boolean = false,
) : InvocationParameter() {
    override suspend fun buildInvocationList(
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

@Serializable
@SerialName("bool_flag")
data class BooleanFlagParameter(
    val variableName: String,
    val flag: String,
) : InvocationParameter() {
    override suspend fun buildInvocationList(
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

suspend fun Iterable<InvocationParameter>.buildSafeBashString(
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


interface ArgumentBuilder {
    suspend fun build(parameter: ApplicationParameter, value: AppParameterValue): String

    companion object Default : ArgumentBuilder {
        override suspend fun build(parameter: ApplicationParameter, value: AppParameterValue): String {
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
                    return license.id
                }

                is ApplicationParameter.Ingress -> {
                    return (value as AppParameterValue.Ingress).id
                }

                is ApplicationParameter.NetworkIP -> {
                    return (value as AppParameterValue.Network).id
                }
            }
        }
    }
}

expect fun safeBashArgument(arg: String): String

suspend fun InvocationParameter.buildInvocationSnippet(
    parameters: AppParametersWithValues,
    builder: ArgumentBuilder = ArgumentBuilder.Default,
): String? {
    return buildInvocationList(parameters, InvocationParameterContext.COMMAND, builder)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { safeBashArgument(it) }
}

suspend fun InvocationParameter.buildEnvironmentValue(
    parameters: AppParametersWithValues,
    builder: ArgumentBuilder = ArgumentBuilder.Default,
): String? {
    return buildInvocationList(parameters, InvocationParameterContext.ENVIRONMENT, builder)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
}

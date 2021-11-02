package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val log = Logger("InvocationParameter")

typealias AppParametersWithValues = Map<ApplicationParameter, AppParameterValue?>

@Serializable
@UCloudApiDoc(
    """
InvocationParameters supply values to either the command-line or environment variables.

Every parameter can run in one of two contexts. They produce a value when combined with a $TYPE_REF ApplicationParameter 
and a $TYPE_REF AppParameterValue:

- __Command line argument:__ Produces zero or more arguments for the command-line
- __Environment variable:__ Produces exactly one value.

For each of the $TYPE_REF InvocationParameter types, we will describe the value(s) they produce. We will also highlight 
notable differences between CLI args and environment variables.
""", importance = 920
)
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
// TODO(Dan): This might not work as intended anymore. I am completely unable to decipher this code.
@UCloudApiDoc("Produces an environment variable (TODO Documentation)", importance = 919)
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
@UCloudApiDoc(
    """
    A static value for an InvocationParameter
    
    This value is static and will always produce only a single value. As a result, you do not need to escape any values
    for this parameter.
""", importance = 919
)
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
@UCloudApiDoc(
    """
    An InvocationParameter which produces value(s) from parameters.

    The parameter receives a list of `variableNames`. Each must reference an $TYPE_REF ApplicationParameter . It is 
    valid to reference both optional and mandatory parameters. This invocation will produce zero values if all the 
    parameters have no value. This is regardless of the prefixes and suffixes.

    The invocation accepts prefixes and suffixes. These will alter the values produced. The global affixes always 
    produce one value each, if supplied. The variable specific affixes produce their own value if 
    `isXVariablePartOfArg`.

    __Example:__ Simple variable
    
    _`VariableInvocationParameter`:_
    
    ```json
    {
        "variableNames": ["myVariable"]
    }
    ```
    
    _Values (`AppParameterValue`):_
    
    ```json
    {
        "myVariable": { "type": "text", "value": "Hello, World!" }
    }
    ```
    
    _Expands to:_
    
    ```bash
    "Hello, World!"
    ```
    
    __Example:__ Global prefix (command line flags)
    
    _`VariableInvocationParameter`:_
    
    ```json
    {
        "variableNames": ["myVariable"],
        "prefixGlobal": "--count"
    }
    ```
    
    _Values (`AppParameterValue`):_
    
    ```json
    {
        "myVariable": { "type": "integer", "value": 42 }
    }
    ```
    
    _Expands to:_
    
    ```bash
    "--count" "42"
    ```
    
    __Example:__ Multiple variables
    
    _`VariableInvocationParameter`:_
    
    ```json
    {
        "variableNames": ["myVariable", "mySecondVariable"],
        "prefixGlobal": "--count"
    }
    ```
    
    _Values (`AppParameterValue`):_
    
    ```json
    {
        "myVariable": { "type": "integer", "value": 42 },
        "mySecondVariable": { "type": "integer", "value": 120 },
    }
    ```
    
    _Expands to:_
    
    ```bash
    "--count" "42" "120"
    ```
    
    __Example:__ Variable prefixes and suffixes
    
    _`VariableInvocationParameter`:_
    
    ```json
    {
        "variableNames": ["myVariable"],
        "prefixGlobal": "--entries",
        "prefixVariable": "--entry",
        "suffixVariable": "--next",
        "isPrefixVariablePartOfArg": true,
        "isSuffixVariablePartOfArg": false
    }
    ```
    
    _Values (`AppParameterValue`):_
    
    ```json
    {
        "myVariable": { "type": "integer", "value": 42 },
    }
    ```
    
    _Expands to:_
    
    ```bash
    "--entries" "--entry42" "--next"
    ```
    
    __Example:__ Complete example
    
    _`VariableInvocationParameter`:_
    
    ```json
    {
        "variableNames": ["myVariable", "mySecondVariable"],
        "prefixGlobal": "--entries",
        "prefixVariable": "--entry",
        "suffixVariable": "--next",
        "suffixGlobal": "--endOfEntries",
        "isPrefixVariablePartOfArg": false,
        "isSuffixVariablePartOfArg": true
    }
    ```
    
    _Values (`AppParameterValue`):_
    
    ```json
    {
        "myVariable": { "type": "integer", "value": 42 },
        "mySecondVariable": { "type": "text", "value": "hello" },
    }
    ```
    
    _Expands to:_
    
    ```bash
    "--entries" "--entry" "42--next" "--entry" "hello--next" "--endOfEntries"
    ```
""", importance = 919
)
data class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String = "",
    val suffixGlobal: String = "",
    val prefixVariable: String = "",
    val suffixVariable: String = "",
    val isPrefixVariablePartOfArg: Boolean = false,
    val isSuffixVariablePartOfArg: Boolean = false,
) : InvocationParameter(), DocVisualizable {
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

    @OptIn(ExperimentalStdlibApi::class)
    override fun visualize(): DocVisualization {
        return DocVisualization.Card(
            this::class.simpleName ?: "",
            buildList {
                if (isPrefixVariablePartOfArg) add(DocStatLine.of("isPrefixVariablePartOfArg" to visualizeValue(true)))
                if (isSuffixVariablePartOfArg) add(DocStatLine.of("isSuffixVariablePartOfArg" to visualizeValue(true)))
                if (prefixGlobal != "") add(DocStatLine.of("prefixGlobal" to visualizeValue(prefixGlobal)))
                if (prefixVariable != "") add(DocStatLine.of("prefixVariable" to visualizeValue(prefixVariable)))
                if (suffixGlobal != "") add(DocStatLine.of("suffixGlobal" to visualizeValue(suffixGlobal)))
                if (suffixVariable != "") add(DocStatLine.of("suffixVariable" to visualizeValue(suffixVariable)))
                if (variableNames.size == 1) {
                    add(DocStatLine.of("variables" to visualizeValue(variableNames.first())))
                } else {
                    add(DocStatLine.of("variables" to visualizeValue(variableNames)))
                }
            },
            emptyList()
        )
    }
}

@Serializable
@SerialName("bool_flag")
@UCloudApiDoc(
    """
    Produces a toggleable command-line flag
    
    The parameter referenced by `variableName` must be of type $TYPE_REF ApplicationParameter.Bool, and the value
    must be $TYPE_REF AppParamValue.Bool . This invocation parameter will produce the `flag` if the variable's value is
    `true`. Otherwise, it will produce no values.
    
    __Example:__ Example (with true value)
    
    _`VariableInvocationParameter`:_
    
    ```json
    {
        "type": "bool_flag",
        "variableName": ["myVariable"],
        "flag": "--example"
    }
    ```
    
    _Values (`AppParameterValue`):_
    
    ```json
    {
        "myVariable": { "type": "bool", "value": true }
    }
    ```
    
    _Expands to:_
    
    ```bash
    "--example"
    ```
    
    __Example:__ Example (with false value)
    
    _`VariableInvocationParameter`:_
    
    ```json
    {
        "type": "bool_flag",
        "variableName": ["myVariable"],
        "flag": "--example"
    }
    ```
    
    _Values (`AppParameterValue`):_
    
    ```json
    {
        "myVariable": { "type": "bool", "value": false }
    }
    ```
    
    _Expands to (nothing):_
    
    ```bash
    
    ```
    
    __Example:__ With spaces
    
    _`VariableInvocationParameter`:_
    
    ```json
    {
        "type": "bool_flag",
        "variableName": ["myVariable"],
        "flag": "--hello world"
    }
    ```
    
    _Values (`AppParameterValue`):_
    
    ```json
    {
        "myVariable": { "type": "bool", "value": true }
    }
    ```
    
    _Expands to:_
    
    ```bash
    "--hello world"
    ```
""", importance = 919
)
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

                is ApplicationParameter.TextArea -> {
                    return (value as AppParameterValue.TextArea).value
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

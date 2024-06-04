package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.WithStringId
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.BashEscaper
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

sealed class ApplicationVerificationException(why: String, httpStatusCode: HttpStatusCode) :
    RPCException(why, httpStatusCode) {
    class DuplicateDefinition(type: String, definitions: List<String>) :
        ApplicationVerificationException(
            "Duplicate definition of $type. " +
                    "Duplicates where: ${definitions.joinToString(", ")}",
            HttpStatusCode.BadRequest
        )

    class BadValue(parameter: String, why: String) :
        ApplicationVerificationException("Parameter '$parameter' received a bad value. $why", HttpStatusCode.BadRequest)

    class BadDefaultValue(parameter: String) :
        ApplicationVerificationException(
            "Invalid defaultValue for parameter '$parameter'",
            HttpStatusCode.BadRequest
        )


    class BadVariableReference(where: String, name: String) :
        ApplicationVerificationException(
            "Variable referenced at $where with name '$name' could not be resolved",
            HttpStatusCode.BadRequest
        )
}

@Serializable
@UCloudApiDoc("""
    The ApplicationType determines how user's interact with an Application
    
    - `BATCH`: A non-interactive $TYPE_REF Application which runs without user input
    - `VNC`: An interactive $TYPE_REF Application exposing a remote desktop interface
    - `WEB`: An interactive $TYPE_REF Application exposing a graphical web interface
""", importance = 980)
enum class ApplicationType {
    @UCloudApiDoc("A non-interactive $TYPE_REF Application which runs without user input")
    BATCH,
    @UCloudApiDoc("An interactive $TYPE_REF Application exposing a remote desktop interface")
    VNC,
    @UCloudApiDoc("An interactive $TYPE_REF Application exposing a graphical web interface")
    WEB
}


private const val TYPE_INPUT_FILE = "input_file"
private const val TYPE_INPUT_DIRECTORY = "input_directory"
private const val TYPE_TEXT = "text"
private const val TYPE_TEXTAREA = "textarea"
private const val TYPE_INTEGER = "integer"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_ENUMERATION = "enumeration"
private const val TYPE_FLOATING_POINT = "floating_point"
private const val TYPE_PEER = "peer"
private const val TYPE_LICENSE_SERVER = "license_server"
private const val TYPE_INGRESS = "ingress"
private const val TYPE_NETWORK_IP = "network_ip"

@Serializable
@UCloudApiDoc("""
    An ApplicationParameter describe a single input parameter to an Application.

    All $TYPE_REF ApplicationParameter s contain metadata used for the presentation in the frontend. This metadata 
    includes a title and help-text. This allows UCloud to create a rich user-interface with widgets which are easy to 
    use. 

    When the user requests the creation of a $TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job, they supply a lot of 
    information. This includes a reference to the $TYPE_REF Application and a set of $TYPE_REF AppParameterValue s. 
    The user must supply a value for every mandatory $TYPE_REF ApplicationParameter . Every parameter has a type 
    associated with it. This type controls the set of valid $TYPE_REF AppParameterValue s it can take.
""", importance = 930)
sealed class ApplicationParameter {
    abstract var name: String
    abstract val optional: Boolean
    abstract val title: String?
    abstract val description: String
    abstract val defaultValue: JsonElement?

    @Serializable
    @SerialName(TYPE_INPUT_FILE)
    @UCloudApiDoc("""
        An input parameter which accepts UFiles of type `FILE`
        
        __Compatible with:__ $TYPE_REF AppParameterValue.File
    """, importance = 929)
    data class InputFile(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_INPUT_DIRECTORY)
    @UCloudApiDoc("""
        An input parameter which accepts UFiles of type `DIRECTORY`
        
        __Compatible with:__ $TYPE_REF AppParameterValue.File
    """, importance = 929)
    data class InputDirectory(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_TEXT)
    @UCloudApiDoc("""
        An input parameter which accepts text
        
        __Compatible with:__ $TYPE_REF AppParameterValue.Text
    """, importance = 929)
    data class Text(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_TEXTAREA)
    data class TextArea(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_INTEGER)
    @UCloudApiDoc("""
        An input parameter which accepts any integer value
        
        __Compatible with:__ $TYPE_REF AppParameterValue.Integer
        
        This parameter can be tweaked using the various options. For example, it is possible to provide a minimum
        and maximum value.
    """, importance = 929)
    data class Integer(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = "",
        val min: Long? = null,
        val max: Long? = null,
        val step: Long? = null,
        val unitName: String? = null
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_FLOATING_POINT)
    @UCloudApiDoc("""
        An input parameter which accepts any floating point value
        
        __Compatible with:__ $TYPE_REF AppParameterValue.FloatingPoint
        
        This parameter can be tweaked using the various options. For example, it is possible to provide a minimum
        and maximum value.
    """, importance = 929)
    data class FloatingPoint(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = "",
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        val unitName: String? = null
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_BOOLEAN)
    @UCloudApiDoc("""
        An input parameter which accepts any boolean value
        
        __Compatible with:__ $TYPE_REF AppParameterValue.Bool
    """, importance = 929)
    data class Bool(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = "",
        val trueValue: String = "true",
        val falseValue: String = "false"
    ) : ApplicationParameter()

    @Serializable
    data class EnumOption(val name: String, val value: String)

    @Serializable
    @SerialName(TYPE_ENUMERATION)
    @UCloudApiDoc("""
        An input parameter which accepts an enum
        
        __Compatible with:__ $TYPE_REF AppParameterValue.Text (Note: the text should match the `value` of the selected 
        option)
    """, importance = 929)
    data class Enumeration(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = "",
        val options: List<EnumOption> = emptyList()
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_PEER)
    @UCloudApiDoc("""
        An input parameter which accepts a peering Job
        
        __Compatible with:__ $TYPE_REF AppParameterValue.Peer
    """, importance = 929)
    class Peer(
        override var name: String = "",
        override val title: String = "",
        override val description: String,
        val suggestedApplication: String? = null
    ) : ApplicationParameter() {
        override val defaultValue: JsonElement? = null
        override val optional = false

        /*
        override var name: String = name
            set(value) {
                if (!value.matches(hostNameRegex)) {
                    throw ApplicationVerificationException.BadValue(
                        value,
                        "Peer parameter '$value' must be a valid hostname!"
                    )
                }

                field = value
            }
         */

        companion object {
            private val hostNameRegex =
                Regex(
                    "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$"
                )
        }
    }

    @Serializable
    @SerialName(TYPE_INGRESS)
    @UCloudApiDoc("""
        An input parameter which accepts a ingress (public link)
        
        __Compatible with:__ $TYPE_REF AppParameterValue.Ingress
    """, importance = 929)
    class Ingress(
        override var name: String = "",
        override val title: String = "",
        override val description: String = "",
        override val optional: Boolean = false,
    ) : ApplicationParameter() {
        override val defaultValue: JsonElement? = null
    }

    @Serializable
    @SerialName(TYPE_LICENSE_SERVER)
    @UCloudApiDoc("""
        An input parameter which accepts a license
        
        __Compatible with:__ $TYPE_REF AppParameterValue.License
    """, importance = 929)
    data class LicenseServer(
        override var name: String = "",
        override var title: String = "",
        override val optional: Boolean = false,
        override val description: String = "",
        val tagged: List<String>
    ) : ApplicationParameter() {
        override val defaultValue: JsonElement? = null
    }

    @Serializable
    @SerialName(TYPE_NETWORK_IP)
    @UCloudApiDoc("""
        An input parameter which accepts an IP address
        
        __Compatible with:__ $TYPE_REF AppParameterValue.Network
    """, importance = 929)
    class NetworkIP(
        override var name: String = "",
        override val title: String = "",
        override val description: String = "",
    ) : ApplicationParameter() {
        override val defaultValue: JsonElement? = null
        override val optional = false
    }
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc(
    """An `AppParameterValue` is value which is supplied to a parameter of an `Application`.
    
Each value type can is type-compatible with one or more `ApplicationParameter`s. The effect of a specific value depends
on its use-site, and the type of its associated parameter.

`ApplicationParameter`s have the following usage sites:

- Invocation: This affects the command line arguments passed to the software.
- Environment variables: This affects the environment variables passed to the software.
- Resources: This only affects the resources which are imported into the software environment. Not all values can be
  used as a resource.
""", importance = 925)
@Serializable
@UCloudApiOwnedBy(AppStore::class)
sealed class AppParameterValue {
    @UCloudApiDoc("""
        A reference to a UCloud file
        
        - __Compatible with:__ `ApplicationParameter.InputFile` and `ApplicationParameter.InputDirectory`
        - __Mountable as a resource:__ ✅ Yes
        - __Expands to:__ The absolute path to the file or directory in the software's environment
        - __Side effects:__ Includes the file or directory in the `Job`'s temporary work directory
            
        The path of the file must be absolute and refers to either a UCloud directory or file.
    """, importance = 924)
    @Serializable
    @SerialName("file")
    data class File(
        @UCloudApiDoc("The absolute path to the file or directory in UCloud")
        val path: String,
        @UCloudApiDoc(
            """Indicates if this file or directory should be mounted as read-only

A provider must reject the request if it does not support read-only mounts when `readOnly = true`.
"""
        )
        var readOnly: Boolean = false,
    ) : AppParameterValue()

    @UCloudApiDoc("""
        A boolean value (true or false)
    
        - __Compatible with:__ `ApplicationParameter.Bool`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ `trueValue` of `ApplicationParameter.Bool` if value is `true` otherwise `falseValue`
        - __Side effects:__ None
    """, importance = 924)
    @Serializable
    @SerialName("boolean")
    data class Bool(val value: Boolean) : AppParameterValue()

    @UCloudApiDoc(
        """A textual value
    
- __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
- __Side effects:__ None

When this is used with an `Enumeration` it must match the value of one of the associated `options`.
"""
    )
    @Serializable
    @SerialName("textarea")
    data class TextArea(val value: String) : AppParameterValue()

    @UCloudApiDoc("""
        A textual value
    
        - __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
        - __Side effects:__ None

        When this is used with an `Enumeration` it must match the value of one of the associated `options`.
    """, importance = 924)
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : AppParameterValue()

    @UCloudApiDoc("""
        An integral value

        - __Compatible with:__ `ApplicationParameter.Integer`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ The number
        - __Side effects:__ None

        Internally this uses a big integer type and there are no defined limits.
    """, importance = 924)
    @Serializable
    @SerialName("integer")
    data class Integer(val value: Long) : AppParameterValue()

    @UCloudApiDoc("""
        A floating point value
    
        - __Compatible with:__ `ApplicationParameter.FloatingPoint`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ The number
        - __Side effects:__ None

        Internally this uses a big decimal type and there are no defined limits.
    """, importance = 924)
    @Serializable
    @SerialName("floating_point")
    data class FloatingPoint(val value: Double) : AppParameterValue()

    @UCloudApiDoc("""
        A reference to a separate UCloud `Job`
    
        - __Compatible with:__ `ApplicationParameter.Peer`
        - __Mountable as a resource:__ ✅ Yes
        - __Expands to:__ The `hostname`
        - __Side effects:__ Configures the firewall to allow bidirectional communication between this `Job` and the peering 
          `Job`
    """, importance = 924)
    @Serializable
    @SerialName("peer")
    data class Peer(val hostname: String, val jobId: String) : AppParameterValue() {
        init {
            if (!hostname.matches(hostNameRegex)) {
                throw RPCException("Invalid hostname: $hostname", HttpStatusCode.BadRequest)
            }

            if (hostname.length > 250) {
                throw RPCException("Hostname is too long: ${hostname.take(250)}...", HttpStatusCode.BadRequest)
            }
        }

    }

    @UCloudApiDoc("""
        A reference to a software license, registered locally at the provider
    
        - __Compatible with:__ `ApplicationParameter.LicenseServer`
        - __Mountable as a resource:__ ❌ No
        - __Expands to:__ `${"$"}{license.address}:${"$"}{license.port}/${"$"}{license.key}` or 
          `${"$"}{license.address}:${"$"}{license.port}` if no key is provided
        - __Side effects:__ None
    """, importance = 924)
    @Serializable
    @SerialName("license_server")
    data class License(override val id: String) : AppParameterValue(), WithStringId

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc("A reference to block storage (Not yet implemented)", importance = 924)
    @Serializable
    @SerialName("block_storage")
    data class BlockStorage(override val id: String) : AppParameterValue(), WithStringId

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc("A reference to block storage (Not yet implemented)", importance = 924)
    @Serializable
    @SerialName("network")
    data class Network(override val id: String) : AppParameterValue(), WithStringId

    @UCloudApiDoc("""
        A reference to an HTTP ingress, registered locally at the provider
    
        - __Compatible with:__ `ApplicationParameter.Ingress`
        - __Mountable as a resource:__ ✅ Yes
        - __Expands to:__ `${"$"}{id}`
        - __Side effects:__ Configures an HTTP ingress for the application's interactive web interface. This interface should
          not perform any validation, that is, the application should be publicly accessible.
    """, importance = 924)
    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @Serializable
    @SerialName("ingress")
    data class Ingress(override val id: String) : AppParameterValue(), WithStringId
}

private val hostNameRegex =
    Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*" +
            "([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$")

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
        return when (context) {
            InvocationParameterContext.COMMAND -> listOf("${"$"}variable")
            InvocationParameterContext.ENVIRONMENT -> listOf("$($variable)")
        }
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

interface WithNameAndVersion {
    val name: String
    val version: String
}

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
@UCloudApiDoc("A type describing a name and version tuple")
data class NameAndVersion(
    override val name: String,
    override val version: String
) : WithNameAndVersion, DocVisualizable {
    override fun toString() = "$name@$version"
    override fun visualize(): DocVisualization = DocVisualization.Inline(toString())
}

@Serializable
@UCloudApiDoc("""
    Metadata associated with an Application
    
    The metadata describes information mostly useful for presentation purposes. The only exception are `name` and
    `version` which are (also) used as identifiers.
""", importance = 999)
data class ApplicationMetadata(
    @UCloudApiDoc("A stable identifier for this Application's name")
    override val name: String,

    @UCloudApiDoc("A stable identifier for this Application's version")
    override val version: String,

    @UCloudApiDoc("A list of authors")
    val authors: List<String>,

    @UCloudApiDoc("A (non-stable) title for this Application, used for presentation")
    val title: String,

    @UCloudApiDoc("A markdown document describing this Application")
    val description: String,

    @UCloudApiDoc("An absolute URL which points to further information about the Application")
    val website: String? = null,

    @UCloudApiDoc("A flag which describes if this Application is publicly accessible")
    val public: Boolean,

    @UCloudApiDoc("Name of the Application within the ApplicationGroup. If not defined, the title will be used.")
    val flavorName: String? = null,

    @UCloudApiDoc("The ApplicationGroup of the Application")
    val group: ApplicationGroup? = null,

    val createdAt: Long = Time.now(),
) : WithNameAndVersion {
    @Deprecated("Replaced with public") @Transient
    val isPublic = public
}

@Serializable
@UCloudApiDoc("""
    Information to the Provider about how to reach the VNC services
    
    Providers must use this information when 
    [opening an interactive session]($CALL_REF_LINK jobs.openInteractiveSession). 
""", importance = 970)
data class VncDescription(
    val password: String? = null,
    val port: Int = 5900
)

@Serializable
@UCloudApiDoc("""
    Information to the Provider about how to reach the web services
    
    Providers must use this information when 
    [opening an interactive session]($CALL_REF_LINK jobs.openInteractiveSession). 
""", importance = 960)
data class WebDescription(
    val port: Int = 80
)

@Serializable
@UCloudApiDoc("""
    Information to the provider about the SSH capabilities of this application
    
    Providers must use this information, if SSH is supported, to correctly configure applications with the appropriate
    keys. See $CALL_REF_LINK jobs.control.browseSshKeys for more information.
""")
data class SshDescription(
    val mode: Mode = Mode.DISABLED
) {
    enum class Mode {
        DISABLED,
        OPTIONAL,
        MANDATORY
    }
}

@Serializable
@UCloudApiDoc("Section describing the module capabilities of an application")
@UCloudApiExperimental(ExperimentalLevel.BETA)
data class ModulesSection(
    val mountPath: String,
    val optional: List<String>
)

@Serializable
@UCloudApiDoc("Information to the Provider about how to launch the container", importance = 950)
data class ContainerDescription(
    val changeWorkingDirectory: Boolean = true,
    val runAsRoot: Boolean = false,
    val runAsRealUser: Boolean = false
) {
    init {
        if (runAsRoot && runAsRealUser) {
            throw ApplicationVerificationException.BadValue(
                "container.runAsRoot/container.runAsRealUser",
                "Cannot runAsRoot and runAsRealUser. These are mutually exclusive."
            )
        }
    }
}

@Serializable
@UCloudApiDoc("""
    The specification for how to invoke an Application

    All $TYPE_REF Application s require a `tool`. The $TYPE_REF Tool specify the concrete computing environment. 
    With the `tool` we get the required software packages and configuration.

    In this environment, we must start some software. Any $TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job launched with
    this $TYPE_REF Application will only run for as long as the software runs. You can specify the command-line 
    invocation through the `invocation` property. Each element in this list produce zero or more arguments for the 
    actual invocation. These $TYPE_REF InvocationParameter s can reference the input `parameters` of the 
    $TYPE_REF Application . In addition, you can set the `environment` variables through the same mechanism.

    All $TYPE_REF Application s have an $TYPE_REF ApplicationType associated with them. This `type` determines how the 
    user interacts with your $TYPE_REF Application . We support the following types:

    - `BATCH`: A non-interactive $TYPE_REF Application which runs without user input
    - `VNC`: An interactive $TYPE_REF Application exposing a remote desktop interface
    - `WEB`:  An interactive $TYPE_REF Application exposing a graphical web interface

    The $TYPE_REF Application must expose information about how to access interactive services. It can do so by 
    setting `vnc` and `web`. Providers must use this information when 
    [opening an interactive session]($CALL_REF_LINK jobs.openInteractiveSession). 

    Users can launch a $TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job with additional `resources`, such as 
    IP addresses and files. The $TYPE_REF Application author specifies the supported resources through the 
    `allowXXX` properties.
""", importance = 990)
data class ApplicationInvocationDescription(
    @UCloudApiDoc("A reference to the Tool used by this Application")
    val tool: ToolReference,

    @UCloudApiDoc("Instructions on how to build the command-line invocation")
    val invocation: List<InvocationParameter>,

    @UCloudApiDoc("The input parameters used by this Application")
    val parameters: List<ApplicationParameter>,

    @Deprecated("No longer in use")
    val outputFileGlobs: List<String>,

    @UCloudApiDoc("The type of this Application, it determines how users will interact with the Application")
    val applicationType: ApplicationType = ApplicationType.BATCH,

    @UCloudApiDoc("Information about how to reach the VNC service")
    val vnc: VncDescription? = null,

    @UCloudApiDoc("Information about how to reach the web service")
    val web: WebDescription? = null,

    @UCloudApiDoc("Information about how the SSH capabilities of this application")
    val ssh: SshDescription? = null,

    @UCloudApiDoc("Hints to the container system about how the Application should be launched")
    val container: ContainerDescription? = null,

    @UCloudApiDoc("Additional environment variables to be added in the environment")
    val environment: Map<String, InvocationParameter>? = null,

    @UCloudApiDoc("Flag to enable/disable support for additional file mounts (default: true for interactive apps)")
    internal val allowAdditionalMounts: Boolean? = null,

    @UCloudApiDoc("Flag to enable/disable support for connecting Jobs together (default: true)")
    internal val allowAdditionalPeers: Boolean? = null,

    @UCloudApiDoc("Flag to enable/disable multiple replicas of this Application (default: false)")
    val allowMultiNode: Boolean = false,

    @UCloudApiDoc("Flag to enable/disable support for public IP (default false)")
    val allowPublicIp: Boolean? = false,

    @UCloudApiDoc("Flag to enable/disable support for public link (default: true for web apps)")
    val allowPublicLink: Boolean? = null,

    @UCloudApiDoc("""
        The file extensions which this Application can handle
        
        This list used as a suffix filter. As a result, this list should typically include the dot.
    """)
    val fileExtensions: List<String> = emptyList(),

    @UCloudApiDoc("Hint used by the frontend to find appropriate license servers")
    val licenseServers: List<String> = emptyList(),

    @UCloudApiDoc("A section describing integration with a module system. " +
            "Currently only valid for `CONTAINER` based applications.")
    val modules: ModulesSection? = null,
) {
    val shouldAllowAdditionalMounts: Boolean
        get() {
            if (allowAdditionalMounts != null) return allowAdditionalMounts
            return applicationType in setOf(ApplicationType.VNC, ApplicationType.WEB)
        }

    val shouldAllowAdditionalPeers: Boolean
        get() {
            if (allowAdditionalPeers != null) return allowAdditionalPeers
            return applicationType in setOf(ApplicationType.VNC, ApplicationType.WEB, ApplicationType.BATCH)
        }
}

interface WithAppMetadata {
    val metadata: ApplicationMetadata
}

interface WithAppInvocation {
    val invocation: ApplicationInvocationDescription
}

interface WithAppFavorite {
    val favorite: Boolean
}

interface WithAllAppTags {
    val tags: List<String>
}

@Serializable
data class ApplicationSummary(
    override val metadata: ApplicationMetadata
) : WithAppMetadata

@Serializable
@UCloudApiDoc("""
    Applications specify the input parameters and invocation of a software package.

    For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).
""", importance = 1000)
@UCloudApiOwnedBy(AppStore::class)
data class Application(
    override val metadata: ApplicationMetadata,
    override val invocation: ApplicationInvocationDescription
) : WithAppMetadata, WithAppInvocation {
    fun withoutInvocation(): ApplicationSummary = ApplicationSummary(metadata)
}

@Serializable
@UCloudApiDoc("""
    Applications specify the input parameters and invocation of a software package.

    For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).
""")
data class ApplicationWithExtension(
    override val metadata: ApplicationMetadata,
    val extensions: List<String>
) : WithAppMetadata

@Serializable
@UCloudApiDoc("""
    Applications specify the input parameters and invocation of a software package.

    For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).
""")
data class ApplicationWithFavoriteAndTags(
    override val metadata: ApplicationMetadata,
    override val invocation: ApplicationInvocationDescription,
    override val favorite: Boolean,
    override val tags: List<String>
) : WithAppMetadata, WithAppInvocation, WithAppFavorite, WithAllAppTags {
    fun withoutInvocation(): ApplicationSummaryWithFavorite = ApplicationSummaryWithFavorite(metadata, favorite, tags)
}

@Serializable
@UCloudApiDoc("""
    Applications specify the input parameters and invocation of a software package.

    For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).
""")
data class ApplicationSummaryWithFavorite(
    override val metadata: ApplicationMetadata,
    override val favorite: Boolean,
    override val tags: List<String>
) : WithAppMetadata, WithAppFavorite, WithAllAppTags

@Serializable
@UCloudApiDoc("The specification of a Tool", importance = 450)
data class NormalizedToolDescription(
    @UCloudApiDoc("The unique name and version tuple")
    val info: NameAndVersion,

    @Deprecated("Use image instead")
    @UCloudApiDoc("Deprecated, use image instead.")
    val container: String? = null,

    @Deprecated("Use-case is unclear")
    @UCloudApiDoc("The default number of nodes")
    val defaultNumberOfNodes: Int,

    @Deprecated("Use-case is unclear")
    @UCloudApiDoc("The default time allocation to use, if none is specified.")
    val defaultTimeAllocation: SimpleDuration,

    @UCloudApiDoc("""
        A list of required 'modules'
        
        The provider decides how to interpret this value. It is intended to be used with a module system of traditional 
        HPC systems.
    """)
    val requiredModules: List<String>,

    @UCloudApiDoc("A list of authors")
    val authors: List<String>,

    @UCloudApiDoc("A title for this Tool used for presentation purposes")
    val title: String,

    @UCloudApiDoc("A description for this Tool used for presentation purposes")
    val description: String,

    @UCloudApiDoc("The backend to use for this Tool")
    val backend: ToolBackend,

    @UCloudApiDoc("A license used for this Tool. Used for presentation purposes.")
    val license: String,

    @UCloudApiDoc("""
        The 'image' used for this Tool
        
        This value depends on the `backend` used for the Tool:
        
        - `DOCKER`: The image is a container image. Typically follows the Docker format.
        - `VIRTUAL_MACHINE`: The image is a reference to a base-image
        
        It is always up to the Provider how to interpret this value. We recommend using the `supportedProviders`
        property to ensure compatibility.
    """)
    val image: String? = null,

    @UCloudApiDoc("""
        A list of supported Providers
        
        This property determines which Providers are supported by this Tool. The backend will not allow a user to
        launch an Application which uses this Tool on a provider not listed in this value.
        
        If no providers are supplied, then this Tool will implicitly support all Providers.
    """)
    val supportedProviders: List<String>? = null,
) {
    override fun toString(): String {
        return "NormalizedToolDescription(info=$info, container='$container')"
    }
}

@Serializable
@UCloudApiDoc("A reference to a Tool")
data class ToolReference(
    override val name: String,
    override val version: String,
    val tool: Tool? = null,
) : WithNameAndVersion

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
@UCloudApiDoc("""
Tools define bundles of software binaries and other assets (e.g. container and virtual machine base-images).

See [Tools](/docs/developer-guide/orchestration/compute/appstore/tools.md) for a more complete discussion.
""", importance = 500)
data class Tool(
    @UCloudApiDoc("The username of the user who created this Tool")
    val owner: String,

    @UCloudApiDoc("Timestamp describing initial creation")
    val createdAt: Long,

    @Deprecated("Tools are immutable")
    @UCloudApiDoc("Timestamp describing most recent modification (Deprecated, Tools are immutable)")
    val modifiedAt: Long,

    @UCloudApiDoc("The specification for this Tool")
    val description: NormalizedToolDescription
)

fun safeBashArgument(arg: String): String = BashEscaper.safeBashArgument(arg)

@Serializable
data class SimpleDuration(val hours: Int, val minutes: Int, val seconds: Int) : DocVisualizable {
    init {
        checkMinimumValue(::seconds, seconds, 0)
        checkMaximumValue(::seconds, seconds, 59)
        checkMinimumValue(::minutes, minutes, 0)
        checkMaximumValue(::minutes, minutes, 59)
    }

    override fun toString() = StringBuilder().apply {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }.toString()

    fun toMillis(): Long {
        return (hours * 60L * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000)
    }

    override fun visualize(): DocVisualization {
        return DocVisualization.Inline("${hours}h ${minutes}m ${seconds}s")
    }

    companion object {
        fun fromMillis(durationMs: Long): SimpleDuration {
            val hours = durationMs / (1000 * 60 * 60)
            val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = ((durationMs % (1000 * 60 * 60)) % (1000 * 60)) / 1000

            return SimpleDuration(hours.toInt(), minutes.toInt(), seconds.toInt())
        }
    }
}

@Serializable
enum class ToolBackend {
    SINGULARITY,
    DOCKER,
    VIRTUAL_MACHINE,
    NATIVE,
}

sealed class ToolVerificationException(why: String, httpStatusCode: HttpStatusCode) :
    RPCException(why, httpStatusCode) {
    class BadValue(parameter: String, reason: String) :
        ToolVerificationException("Parameter '$parameter' received a bad value. $reason", HttpStatusCode.BadRequest)
}

@Serializable
enum class ApplicationAccessRight {
    LAUNCH
}

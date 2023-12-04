package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.TYPE_REF
import dk.sdu.cloud.calls.UCloudApiDoc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
        override val optional: Boolean = false
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
        override val optional: Boolean = false
    ) : ApplicationParameter() {
        override val defaultValue: JsonElement? = null
    }
}

package dk.sdu.cloud.app.store.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

private const val TYPE_INPUT_FILE = "input_file"
private const val TYPE_INPUT_DIRECTORY = "input_directory"
private const val TYPE_TEXT = "text"
private const val TYPE_INTEGER = "integer"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_ENUMERATION = "enumeration"
private const val TYPE_FLOATING_POINT = "floating_point"
private const val TYPE_PEER = "peer"
private const val TYPE_LICENSE_SERVER = "license_server"
private const val TYPE_INGRESS = "ingress"
private const val TYPE_NETWORK_IP = "network_ip"

@Serializable
sealed class ApplicationParameter {
    abstract var name: String
    abstract val optional: Boolean
    abstract val title: String?
    abstract val description: String
    abstract val defaultValue: JsonElement?

    @Serializable
    @SerialName(TYPE_INPUT_FILE)
    data class InputFile(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_INPUT_DIRECTORY)
    data class InputDirectory(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_TEXT)
    data class Text(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: JsonElement? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameter()

    @Serializable
    @SerialName(TYPE_INTEGER)
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
    class Ingress(
        override var name: String = "",
        override val title: String = "",
        override val description: String = "",
    ) : ApplicationParameter() {
        override val defaultValue: JsonElement? = null
        override val optional = false
    }

    @Serializable
    @SerialName(TYPE_LICENSE_SERVER)
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
    class NetworkIP(
        override var name: String = "",
        override val title: String = "",
        override val description: String = "",
    ) : ApplicationParameter() {
        override val defaultValue: JsonElement? = null
        override val optional = false
    }
}

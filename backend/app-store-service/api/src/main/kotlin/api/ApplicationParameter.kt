package dk.sdu.cloud.app.store.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.math.BigInteger

private const val TYPE_INPUT_FILE = "input_file"
private const val TYPE_INPUT_DIRECTORY = "input_directory"
private const val TYPE_TEXT = "text"
private const val TYPE_INTEGER = "integer"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_ENUMERATION = "enumeration"
private const val TYPE_FLOATING_POINT = "floating_point"
private const val TYPE_PEER = "peer"
private const val TYPE_LICENSE_SERVER = "license_server"

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApplicationParameter.InputFile::class, name = TYPE_INPUT_FILE),
    JsonSubTypes.Type(value = ApplicationParameter.InputDirectory::class, name = TYPE_INPUT_DIRECTORY),
    JsonSubTypes.Type(value = ApplicationParameter.Text::class, name = TYPE_TEXT),
    JsonSubTypes.Type(value = ApplicationParameter.Integer::class, name = TYPE_INTEGER),
    JsonSubTypes.Type(value = ApplicationParameter.Bool::class, name = TYPE_BOOLEAN),
    JsonSubTypes.Type(value = ApplicationParameter.FloatingPoint::class, name = TYPE_FLOATING_POINT),
    JsonSubTypes.Type(value = ApplicationParameter.Peer::class, name = TYPE_PEER),
    JsonSubTypes.Type(value = ApplicationParameter.Enumeration::class, name = TYPE_ENUMERATION),
    JsonSubTypes.Type(value = ApplicationParameter.LicenseServer::class, name = TYPE_LICENSE_SERVER)
)
sealed class ApplicationParameter(val type: String) {
    abstract var name: String
    abstract val optional: Boolean
    abstract val title: String
    abstract val description: String
    abstract val defaultValue: Any?

    data class InputFile(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = name,
        override val description: String = ""
    ) : ApplicationParameter(TYPE_INPUT_FILE)

    data class InputDirectory(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = name,
        override val description: String = ""
    ) : ApplicationParameter(TYPE_INPUT_DIRECTORY)

    data class Text(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = name,
        override val description: String = ""
    ) : ApplicationParameter(TYPE_TEXT)

    data class Integer(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = name,
        override val description: String = "",
        val min: BigInteger? = null,
        val max: BigInteger? = null,
        val step: BigInteger? = null,
        val unitName: String? = null
    ) : ApplicationParameter(TYPE_INTEGER)

    data class FloatingPoint(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = name,
        override val description: String = "",
        val min: BigDecimal? = null,
        val max: BigDecimal? = null,
        val step: BigDecimal? = null,
        val unitName: String? = null
    ) : ApplicationParameter(TYPE_FLOATING_POINT)

    data class Bool(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = name,
        override val description: String = "",
        val trueValue: String = "true",
        val falseValue: String = "false"
    ) : ApplicationParameter(TYPE_BOOLEAN)

    data class EnumOption(val name: String, val value: String)
    data class Enumeration(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = name,
        override val description: String = "",
        val options: List<EnumOption> = emptyList()
    ) : ApplicationParameter(TYPE_ENUMERATION)

    class Peer(
        name: String = "",
        override val title: String,
        override val description: String,
        val suggestedApplication: String? = null
    ) : ApplicationParameter(TYPE_PEER) {
        override val defaultValue: Any? = null
        override val optional = false

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

        companion object {
            private val hostNameRegex =
                Regex(
                    "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$"
                )
        }
    }

    data class LicenseServer(
        override var name: String = "",
        override var title: String,
        override val optional: Boolean = false,
        override val description: String = "",
        val tagged: List<String>
    ) : ApplicationParameter(TYPE_LICENSE_SERVER) {
        override val defaultValue: Any? = null
    }
}

package app.store.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApplicationParameterYaml.InputFile::class, name = "input_file"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.InputDirectory::class, name = "input_directory"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.Text::class, name = "text"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.TextArea::class, name = "textarea"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.Integer::class, name = "integer"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.FloatingPoint::class, name = "floating_point"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.Bool::class, name = "boolean"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.Enumeration::class, name = "enumeration"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.Peer::class, name = "peer"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.LicenseServer::class, name = "license_server"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.Ingress::class, name = "ingress"),
    JsonSubTypes.Type(value = ApplicationParameterYaml.NetworkIP::class, name = "network_ip"),
)
sealed class ApplicationParameterYaml {
    abstract var name: String
    abstract val optional: Boolean
    abstract val title: String?
    abstract val description: String
    abstract val defaultValue: Any?

    data class InputFile(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameterYaml()

    data class InputDirectory(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameterYaml()

    data class Text(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameterYaml()

    data class TextArea(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = "",
        override val description: String = ""
    ) : ApplicationParameterYaml()

    data class Integer(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = "",
        override val description: String = "",
        val min: Long? = null,
        val max: Long? = null,
        val step: Long? = null,
        val unitName: String? = null
    ) : ApplicationParameterYaml()

    data class FloatingPoint(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = "",
        override val description: String = "",
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        val unitName: String? = null
    ) : ApplicationParameterYaml()

    data class Bool(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = "",
        override val description: String = "",
        val trueValue: String = "true",
        val falseValue: String = "false"
    ) : ApplicationParameterYaml()

    data class EnumOption(val name: String, val value: String)

    data class Enumeration(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Any? = null,
        override val title: String = "",
        override val description: String = "",
        val options: List<EnumOption> = emptyList()
    ) : ApplicationParameterYaml()

    class Peer(
        override var name: String = "",
        override val title: String = "",
        override val description: String,
        val suggestedApplication: String? = null
    ) : ApplicationParameterYaml() {
        override val defaultValue: Any? = null
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

    class Ingress(
        override var name: String = "",
        override val title: String = "",
        override val description: String = "",
    ) : ApplicationParameterYaml() {
        override val defaultValue: Any? = null
        override val optional = false
    }

    data class LicenseServer(
        override var name: String = "",
        override var title: String = "",
        override val optional: Boolean = false,
        override val description: String = "",
        val tagged: List<String>
    ) : ApplicationParameterYaml() {
        override val defaultValue: Any? = null
    }

    class NetworkIP(
        override var name: String = "",
        override val title: String = "",
        override val description: String = "",
    ) : ApplicationParameterYaml() {
        override val defaultValue: Any? = null
        override val optional = false
    }
}

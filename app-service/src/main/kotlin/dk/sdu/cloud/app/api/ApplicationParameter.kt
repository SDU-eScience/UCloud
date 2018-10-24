package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

private const val TYPE_INPUT_FILE = "input_file"
private const val TYPE_INPUT_DIRECTORY = "input_directory"
private const val TYPE_TEXT = "text"
private const val TYPE_INTEGER = "integer"
private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_FLOATING_POINT = "floating_point"

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
    JsonSubTypes.Type(value = ApplicationParameter.FloatingPoint::class, name = TYPE_FLOATING_POINT)
)
sealed class ApplicationParameter<V : ParsedApplicationParameter>(val type: String) {
    abstract var name: String
    abstract val optional: Boolean
    abstract val title: String
    abstract val description: String
    abstract val defaultValue: V?

    protected abstract fun internalMap(inputParameter: Any): V
    abstract fun toInvocationArgument(entry: V): String

    fun map(inputParameter: Any?): V? {
        return if (inputParameter == null) {
            if (!optional) {
                throw IllegalArgumentException("Missing value for parameter '$name'")
            } else {
                defaultValue
            }
        } else {
            internalMap(inputParameter)
        }
    }

    data class InputFile(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: FileTransferDescription? = null,
        override val title: String = name,
        override val description: String = ""
    ) : ApplicationParameter<FileTransferDescription>(TYPE_INPUT_FILE) {
        override fun internalMap(inputParameter: Any): FileTransferDescription {
            @Suppress("UNCHECKED_CAST")
            val params = inputParameter as? Map<String, Any> ?: throw IllegalArgumentException("Invalid user input")
            val source = params["source"] as String? ?: throw IllegalArgumentException("Missing source property")
            val destination =
                params["destination"] as String? ?: throw IllegalArgumentException("Missing destination property")

            return FileTransferDescription(source, destination)
        }

        override fun toInvocationArgument(entry: FileTransferDescription): String = entry.destination
    }

    data class InputDirectory(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: FileTransferDescription? = null,
        override val title: String = name,
        override val description: String = ""
    ) : ApplicationParameter<FileTransferDescription>(TYPE_INPUT_DIRECTORY) {
        override fun internalMap(inputParameter: Any): FileTransferDescription {
            @Suppress("UNCHECKED_CAST")
            val params = inputParameter as? Map<String, Any> ?: throw IllegalArgumentException("Invalid user input")
            val source = params["source"] as String? ?: throw IllegalArgumentException("Missing source property")
            val destination =
                params["destination"] as String? ?: throw IllegalArgumentException("Missing destination property")

            return FileTransferDescription(source, destination)
        }

        override fun toInvocationArgument(entry: FileTransferDescription): String =
            entry.destination.removeSuffix("/") + "/"
    }

    data class Text(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: StringApplicationParameter? = null,
        override val title: String = name,
        override val description: String = ""
    ) : ApplicationParameter<StringApplicationParameter>(TYPE_TEXT) {
        override fun internalMap(inputParameter: Any): StringApplicationParameter =
            StringApplicationParameter(inputParameter.toString())

        override fun toInvocationArgument(entry: StringApplicationParameter): String = entry.value
    }

    data class Integer(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: IntApplicationParameter? = null,
        override val title: String = name,
        override val description: String = "",
        val min: Int? = null,
        val max: Int? = null,
        val step: Int? = null,
        val unitName: String? = null
    ) : ApplicationParameter<IntApplicationParameter>(TYPE_INTEGER) {
        override fun internalMap(inputParameter: Any): IntApplicationParameter =
            IntApplicationParameter((inputParameter as? Int) ?: inputParameter.toString().toInt())

        override fun toInvocationArgument(entry: IntApplicationParameter): String = entry.value.toString()
    }

    data class FloatingPoint(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: DoubleApplicationParameter? = null,
        override val title: String = name,
        override val description: String = "",
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        val unitName: String? = null
    ) : ApplicationParameter<DoubleApplicationParameter>(TYPE_FLOATING_POINT) {
        override fun internalMap(inputParameter: Any): DoubleApplicationParameter =
            DoubleApplicationParameter((inputParameter as? Double) ?: inputParameter.toString().toDouble())

        override fun toInvocationArgument(entry: DoubleApplicationParameter): String = entry.value.toString()
    }

    data class Bool(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: BooleanApplicationParameter? = null,
        override val title: String = name,
        override val description: String = "",
        val trueValue: String = "true",
        val falseValue: String = "false"
    ) : ApplicationParameter<BooleanApplicationParameter>(TYPE_BOOLEAN) {
        override fun internalMap(inputParameter: Any): BooleanApplicationParameter =
            BooleanApplicationParameter((inputParameter as? Boolean) ?: inputParameter.toString().toBoolean())

        override fun toInvocationArgument(entry: BooleanApplicationParameter): String =
            if (entry.value) trueValue else falseValue
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FileTransferDescription::class, name = "file"),
    JsonSubTypes.Type(value = BooleanApplicationParameter::class, name = "bool"),
    JsonSubTypes.Type(value = IntApplicationParameter::class, name = "int"),
    JsonSubTypes.Type(value = DoubleApplicationParameter::class, name = "double"),
    JsonSubTypes.Type(value = StringApplicationParameter::class, name = "string")
)
sealed class ParsedApplicationParameter

data class FileTransferDescription(val source: String, val destination: String) : ParsedApplicationParameter()
data class BooleanApplicationParameter(val value: Boolean) : ParsedApplicationParameter()
data class IntApplicationParameter(val value: Int) : ParsedApplicationParameter()
data class DoubleApplicationParameter(val value: Double) : ParsedApplicationParameter()
data class StringApplicationParameter(val value: String) : ParsedApplicationParameter()

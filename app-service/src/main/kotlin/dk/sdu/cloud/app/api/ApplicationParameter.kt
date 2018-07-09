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
sealed class ApplicationParameter<V : Any>(val type: String) {
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
        override val defaultValue: String? = null,
        override val title: String = name,
        override val description: String = ""
    ) : ApplicationParameter<String>(TYPE_TEXT) {
        override fun internalMap(inputParameter: Any): String = inputParameter.toString()

        override fun toInvocationArgument(entry: String): String = entry
    }

    data class Integer(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Int? = null,
        override val title: String = name,
        override val description: String = "",
        val min: Int? = null,
        val max: Int? = null,
        val step: Int? = null,
        val unitName: String? = null
    ) : ApplicationParameter<Int>(TYPE_INTEGER) {
        override fun internalMap(inputParameter: Any): Int =
            (inputParameter as? Int) ?: inputParameter.toString().toInt()

        override fun toInvocationArgument(entry: Int): String = entry.toString()
    }

    data class FloatingPoint(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Double? = null,
        override val title: String = name,
        override val description: String = "",
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        val unitName: String? = null
    ) : ApplicationParameter<Double>(TYPE_FLOATING_POINT) {
        override fun internalMap(inputParameter: Any): Double =
            (inputParameter as? Double) ?: inputParameter.toString().toDouble()

        override fun toInvocationArgument(entry: Double): String = entry.toString()
    }

    data class Bool(
        override var name: String = "",
        override val optional: Boolean = false,
        override val defaultValue: Boolean? = null,
        override val title: String = name,
        override val description: String = "",
        val trueValue: String = "true",
        val falseValue: String = "false"
    ) : ApplicationParameter<Boolean>(TYPE_BOOLEAN) {
        override fun internalMap(inputParameter: Any): Boolean =
            (inputParameter as? Boolean) ?: inputParameter.toString().toBoolean()

        override fun toInvocationArgument(entry: Boolean): String = if (entry) trueValue else falseValue
    }
}

data class FileTransferDescription(val source: String, val destination: String)

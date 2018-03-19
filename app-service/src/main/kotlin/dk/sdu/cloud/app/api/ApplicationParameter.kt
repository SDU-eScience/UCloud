package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApplicationParameter.InputFile::class, name = "input_file"),
    JsonSubTypes.Type(value = ApplicationParameter.Text::class, name = "text"),
    JsonSubTypes.Type(value = ApplicationParameter.Integer::class, name = "integer"),
    JsonSubTypes.Type(value = ApplicationParameter.Bool::class, name = "boolean"),
    JsonSubTypes.Type(value = ApplicationParameter.FloatingPoint::class, name = "floating_point")
)
sealed class ApplicationParameter<V : Any> {
    abstract val name: String
    abstract val optional: Boolean
    abstract val prettyName: String
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
        override val name: String,
        override val optional: Boolean,
        override val defaultValue: FileTransferDescription? = null,
        override val prettyName: String = name,
        override val description: String = ""
    ) : ApplicationParameter<FileTransferDescription>() {
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

    data class Text(
        override val name: String,
        override val optional: Boolean,
        override val defaultValue: String? = null,
        override val prettyName: String = name,
        override val description: String = ""
    ) : ApplicationParameter<String>() {
        override fun internalMap(inputParameter: Any): String = inputParameter.toString()

        override fun toInvocationArgument(entry: String): String = entry
    }

    data class Integer(
        override val name: String,
        override val optional: Boolean,
        override val defaultValue: Int? = null,
        override val prettyName: String = name,
        override val description: String = "",
        val min: Int? = null,
        val max: Int? = null,
        val step: Int? = null,
        val unitName: String? = null
    ) : ApplicationParameter<Int>() {
        override fun internalMap(inputParameter: Any): Int =
            (inputParameter as? Int) ?: inputParameter.toString().toInt()

        override fun toInvocationArgument(entry: Int): String = entry.toString()
    }

    data class FloatingPoint(
        override val name: String,
        override val optional: Boolean,
        override val defaultValue: Double? = null,
        override val prettyName: String = name,
        override val description: String = "",
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        val unitName: String? = null
    ) : ApplicationParameter<Double>() {
        override fun internalMap(inputParameter: Any): Double =
            (inputParameter as? Double) ?: inputParameter.toString().toDouble()

        override fun toInvocationArgument(entry: Double): String = entry.toString()
    }

    data class Bool(
        override val name: String,
        override val optional: Boolean,
        override val defaultValue: Boolean? = null,
        override val prettyName: String = name,
        override val description: String = "",
        val trueValue: String = "true",
        val falseValue: String = "false"
    ) : ApplicationParameter<Boolean>() {
        override fun internalMap(inputParameter: Any): Boolean =
            (inputParameter as? Boolean) ?: inputParameter.toString().toBoolean()

        override fun toInvocationArgument(entry: Boolean): String = if (entry) trueValue else falseValue
    }
}

data class FileTransferDescription(val source: String, val destination: String)

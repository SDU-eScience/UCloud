package org.esciencecloud.abc.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes(
        JsonSubTypes.Type(value = ApplicationParameter.InputFile::class, name = "input_file"),
        JsonSubTypes.Type(value = ApplicationParameter.OutputFile::class, name = "output_file"),
        JsonSubTypes.Type(value = ApplicationParameter.Text::class, name = "text"),
        JsonSubTypes.Type(value = ApplicationParameter.Integer::class, name = "integer"),
        JsonSubTypes.Type(value = ApplicationParameter.FloatingPoint::class, name = "floating_point"))
sealed class ApplicationParameter<V : Any> {
    abstract val name: String
    abstract fun map(inputParameter: Any): V
    abstract fun toInvocationArgument(entry: V): String

    data class InputFile(override val name: String) : ApplicationParameter<FileTransferDescription>() {
        override fun map(inputParameter: Any): FileTransferDescription {
            @Suppress("UNCHECKED_CAST")
            val params = inputParameter as? Map<String, Any> ?: throw IllegalArgumentException("Invalid user input")
            val source = params["source"] as String? ?: throw IllegalArgumentException("Missing source property")
            val destination = params["destination"] as String? ?:
                    throw IllegalArgumentException("Missing destination property")

            return FileTransferDescription(source, destination)
        }

        override fun toInvocationArgument(entry: FileTransferDescription): String = entry.destination
    }

    data class OutputFile(override val name: String) : ApplicationParameter<FileTransferDescription>() {
        override fun map(inputParameter: Any): FileTransferDescription {
            @Suppress("UNCHECKED_CAST")
            val params = inputParameter as? Map<String, Any> ?: throw IllegalArgumentException("Invalid user input")
            val source = params["source"] as String? ?: throw IllegalArgumentException("Missing source property")
            val destination = params["destination"] as String? ?:
                    throw IllegalArgumentException("Missing destination property")

            return FileTransferDescription(source, destination)
        }

        override fun toInvocationArgument(entry: FileTransferDescription): String = entry.source
    }

    data class Text(override val name: String) : ApplicationParameter<String>() {
        override fun map(inputParameter: Any): String = inputParameter.toString()

        override fun toInvocationArgument(entry: String): String = entry
    }

    data class Integer(override val name: String, val min: Int? = null, val max: Int?) : ApplicationParameter<Int>() {
        override fun map(inputParameter: Any): Int = (inputParameter as? Int) ?: inputParameter.toString().toInt()

        override fun toInvocationArgument(entry: Int): String = entry.toString()
    }

    data class FloatingPoint(
            override val name: String,
            val min: Double?,
            val max: Double?
    ) : ApplicationParameter<Double>() {
        override fun map(inputParameter: Any): Double =
                (inputParameter as? Double) ?: inputParameter.toString().toDouble()

        override fun toInvocationArgument(entry: Double): String = entry.toString()
    }
}

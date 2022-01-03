package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpQueryParameter
import dk.sdu.cloud.calls.http
import io.ktor.application.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
class ParamsParsing(
    private val applicationCall: ApplicationCall,
    private val call: CallDescription<*, *, *>,
) : AbstractDecoder() {
    private var lastReadIdx: Int = -1
    private var lastReadStruct = -1
    private var lastRead: String? = null
    private val didNest = HashSet<String>()
    private val parameters = (call.http.params?.parameters ?: emptyList()).groupBy {
        if (it is HttpQueryParameter.Property<*>) {
            it.nestedInside
        } else {
            null
        }
    }
    private val nestedStructures = parameters.keys.toList()
    private var nestedIdx = 0
    private var elementIndex = 0
    override val serializersModule: SerializersModule = EmptySerializersModule
    private val value: String?
        get() {
            if (lastReadIdx == elementIndex && lastReadStruct == nestedIdx) {
                return lastRead
            } else {
                when (val param = parameters[nestedStructures[nestedIdx]]!![elementIndex - 1]) {
                    is HttpQueryParameter.Property<*> -> {
                        lastRead = applicationCall.request.queryParameters[param.property]
                        lastReadIdx = elementIndex
                        lastReadStruct = nestedIdx
                    }
                }
            }
            return lastRead
        }

    override fun decodeBoolean(): Boolean {
        return value == "true"
    }

    override fun decodeByte(): Byte {
        return value?.toByteOrNull() ?: throw SerializationException("$value is not a valid byte")
    }

    override fun decodeChar(): Char {
        return value?.firstOrNull() ?: throw SerializationException("$value is not a valid char")
    }

    override fun decodeDouble(): Double {
        return value?.toDoubleOrNull() ?: throw SerializationException("$value is not a valid double")
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val firstTry = enumDescriptor.elementNames.indexOf(value).takeIf { it != -1 }
        if (firstTry != null) return firstTry
        return enumDescriptor.elementNames.indexOfFirst { it.equals(value, ignoreCase = true) }.takeIf { it != -1 }
            ?: throw SerializationException("$value is not a valid enum. is null = ${value == null}")
    }

    override fun decodeFloat(): Float {
        return value?.toFloatOrNull() ?: throw SerializationException("$value is not a valid float")
    }

    override fun decodeInt(): Int {
        return value?.toIntOrNull() ?: throw SerializationException("$value is not a valid int")
    }

    override fun decodeLong(): Long {
        return value?.toLongOrNull() ?: throw SerializationException("$value is not a valid long")
    }

    override fun decodeShort(): Short {
        return value?.toShortOrNull() ?: throw SerializationException("$value is not a valid short")
    }

    override fun decodeString(): String {
        return value ?: throw SerializationException("$value was string")
    }

    override fun decodeNotNullMark(): Boolean {
        return value != null
    }

    override fun decodeNull(): Nothing? = null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val nestedField = nestedStructures[nestedIdx]
        val currentStruct = parameters[nestedField]!!
        if (elementIndex == currentStruct.size) {
            nestedIdx++
            elementIndex = 0
            return CompositeDecoder.DECODE_DONE
        }
        val param = currentStruct[elementIndex++]
        return when (param) {
            is HttpQueryParameter.Property<*> -> {
                if (nestedField != null && nestedField !in didNest) {
                    didNest.add(nestedField)
                    elementIndex = 0
                    descriptor.getElementIndex(nestedField)
                } else {
                    descriptor.getElementIndex(param.property)
                }
            }
            else -> error("unknown type $param")
        }
    }
}

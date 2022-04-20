package dk.sdu.cloud.utils

import yaml.*
import kotlinx.cinterop.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.serialization.* import kotlinx.serialization.encoding.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.modules.*

class YamlDecoder(
    private val parser: yaml_parser_t,
) : AbstractDecoder() {
    private val arena = Arena()
    private val event = arena.alloc<yaml_event_t>()
    private var listIdx = 0
    private var firstEvent = true

    override val serializersModule: SerializersModule = EmptySerializersModule

    override fun decodeBoolean(): Boolean {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a boolean")
        return scalar.value == "true"
    }

    override fun decodeByte(): Byte {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a byte")
        return scalar.value.toByteOrNull() ?: deserializationError("Expected a numeric value")
    }

    override fun decodeChar(): Char {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a char")
        if (scalar.value.isEmpty()) deserializationError("Expected a character")
        return scalar.value[0]
    }

    override fun decodeDouble(): Double {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a double")
        return scalar.value.toDoubleOrNull() ?: deserializationError("Expected a numeric value")
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val value = decodeString()
        val firstTry = enumDescriptor.elementNames.indexOf(value).takeIf { it != -1 }
        if (firstTry != null) return firstTry
        return enumDescriptor.elementNames.indexOfFirst { it.equals(value, ignoreCase = true) }.takeIf { it != -1 }
            ?: deserializationError("$value is not a valid enum")
    }

    override fun decodeFloat(): Float {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a float")
        return scalar.value.toFloatOrNull() ?: deserializationError("Expected a numeric value")
    }

    override fun decodeInt(): Int {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected an int")
        return scalar.value.toIntOrNull() ?: deserializationError("Expected a numeric value")
    }

    override fun decodeLong(): Long {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a long")
        return scalar.value.toLongOrNull() ?: deserializationError("Expected a numeric value")
    }

    override fun decodeShort(): Short {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a short")
        return scalar.value.toShortOrNull() ?: deserializationError("Expected a numeric value")
    }

    override fun decodeString(): String {
        val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a string")
        return scalar.value
    }

    override fun decodeNotNullMark(): Boolean {
        val scalar = (lastConsumedToken as? YamlEvent.Scalar) ?: return true
        return scalar.value != "null"
    }

    override fun decodeNull(): Nothing? = null
    
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (descriptor.kind == StructureKind.LIST) {
            listIdx = 0
            return this
        }

        if (descriptor.kind != StructureKind.CLASS) return this

        if (consumeEvent() != YamlEvent.BeginStruct) {
            deserializationError("Expect start of structure")
        }
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (descriptor.kind != StructureKind.CLASS) return
        if (consumeEvent() != YamlEvent.EndStruct) {
            deserializationError("Expect end of structure")
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (descriptor.kind == StructureKind.LIST) {
            if (listIdx == 0) {
                if (lastConsumedToken == YamlEvent.EndSequence) return CompositeDecoder.DECODE_DONE
            } else {
                val ev = fetchEvent() ?: return CompositeDecoder.DECODE_DONE
                if (ev == YamlEvent.EndSequence) return CompositeDecoder.DECODE_DONE
            }
            return listIdx++
        }

        while (true) {
            val ev = fetchEvent() ?: return CompositeDecoder.DECODE_DONE
            if (ev == YamlEvent.EndStruct) return CompositeDecoder.DECODE_DONE
            if (ev !is YamlEvent.Scalar) deserializationError("Expected a new key")

            val elementIndex = descriptor.getElementIndex(ev.value)
            if (elementIndex == CompositeDecoder.UNKNOWN_NAME) {
                skipCurrentContext()
            } else {
                fetchEvent() ?: deserializationError("Expected a value")
                return elementIndex
            }
        }
    }

    private fun deserializationError(message: String): Nothing {
        throw SerializationException(message)
    }

    private var lastConsumedToken: YamlEvent? = null
    private fun consumeEvent(): YamlEvent {
        if (firstEvent) {
            fetchEvent()
            firstEvent = false
            return consumeEvent()
        }
        val result = lastConsumedToken ?: error("lastConsumedToken should not be null")
        lastConsumedToken = null
        return result
    }

    private fun skipCurrentContext() {
        val startDocDepth = documentDepth
        val startSeqDepth = sequenceDepth
        fetchEvent() ?: deserializationError("Unexpected end of document")

        if (documentDepth != startDocDepth) {
            while (documentDepth >= startDocDepth) {
                fetchEvent() ?: deserializationError("Unexpected end of document")
            }
        } else if (sequenceDepth != startSeqDepth) {
            while (sequenceDepth >= startSeqDepth) {
                fetchEvent() ?: deserializationError("Unexpected end of document")
            }
        }
    }

    private var documentDepth = 0
    private var sequenceDepth = 0
    private fun fetchEvent(): YamlEvent? {
        var result: YamlEvent? = null
        while (result == null) {
            if (yaml_parser_parse(parser.ptr, event.ptr) != 1) {
                throw SerializationException("Invalid YAML document")
            }


            when (event.type) {
                yaml_event_type_e.YAML_SCALAR_EVENT -> {
                    val ev = event.data.scalar
                    val value = ev.value?.readBytes(ev.length.toInt())?.toKString() ?: ""
                    val isQuoted = ev.quoted_implicit
                    result = YamlEvent.Scalar(value, isQuoted == 1)
                }

                yaml_event_type_e.YAML_MAPPING_START_EVENT -> {
                    result = YamlEvent.BeginStruct
                    documentDepth++
                }

                yaml_event_type_e.YAML_MAPPING_END_EVENT -> {
                    result = YamlEvent.EndStruct
                    documentDepth--
                }

                yaml_event_type_e.YAML_SEQUENCE_START_EVENT -> {
                    sequenceDepth++
                }

                yaml_event_type_e.YAML_SEQUENCE_END_EVENT -> {
                    result = YamlEvent.EndSequence
                    sequenceDepth--
                }

                yaml_event_type_e.YAML_STREAM_END_EVENT -> {
                    lastConsumedToken = null
                    return null
                }

                else -> {
                    // Ignored
                }
            }
        }

        lastConsumedToken = result
        return result
    }

    private sealed class YamlEvent {
        data class Scalar(val value: String, val quoted: Boolean) : YamlEvent()
        object BeginStruct : YamlEvent()
        object EndStruct : YamlEvent()
        object EndSequence : YamlEvent()
    }
}

object Yaml {
    fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        memScoped {
            val document = string.encodeToByteArray().toUByteArray().pin()
            defer { document.unpin() }

            val parser = alloc<yaml_parser_t>()
            yaml_parser_initialize(parser.ptr)
            yaml_parser_set_input_string(parser.ptr, document.addressOf(0), document.get().size.toULong())
            defer { yaml_parser_delete(parser.ptr) }

            return YamlDecoder(parser).decodeSerializableValue(deserializer)
        }
    }
}

inline fun <reified T> Yaml.decodeFromString(string: String): T {
    return decodeFromString(serializer<T>(), string)
}


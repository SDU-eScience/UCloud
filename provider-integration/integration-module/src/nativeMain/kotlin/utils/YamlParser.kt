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

    // NOTE(Dan): For the entire decoding process, fetchEvent() will always be called prior to one of the decodeXXX()
    // functions. The only exception to this is on the first invocation of a decodeXXX() function. This flag causes
    // consumeEvent() to fetch an event for the first event.
    private var firstEvent = true

    // NOTE(Dan): Keeps track of how many items we have parsed in the current sequence
    private var listIdx = 0

    // NOTE(Dan): Keeps track of how many items we have parsed in a map entry. There are two entries in total, a key
    // and a value.
    private var mapIdx = 0

    // NOTE(Dan): Used to keep track of progress when searching for a polymorphic type marker. If the value is 0, then
    // we must search for a type marker. If the value is 1, then we must consume the value itself. For all other
    // values, we are not searching for a type marker.
    private var sealedTypeSearch = -1

    // NOTE(Dan): Keeps track of the depth of a sealed class. This is required since we most return an explicit
    // DECODE_DONE for a sealed class immediately following the close of the wrapping struct. We will not receive any
    // events from the YAML parser which indicates this, so we must keep track of it ourselves. This is used in
    // conjunction with didJustCloseStruct, which is set to true immediately after consuming an EndStruct.
    private val sealedClassLocations = ArrayDeque<Int>()
    private var didJustCloseStruct = false

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
        if (sealedTypeSearch == 0) {
            // NOTE(Dan): This indicates that we are searching for a polymorphic type marker. We use
            // scanForPolymorphicType() to find the value and increament the search progress. This tells the
            // serializer that we are ready to consume the value.
            sealedTypeSearch++
            return scanForPolymorphicType() ?: deserializationError("Could not find type tag")
        } else {
            val scalar = (consumeEvent() as? YamlEvent.Scalar) ?: deserializationError("Expected a string")
            return scalar.value
        }
    }

    override fun decodeNotNullMark(): Boolean {
        val scalar = (lastConsumedToken as? YamlEvent.Scalar) ?: return true
        return scalar.value != "null"
    }

    override fun decodeNull(): Nothing? = null
    
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (descriptor.kind == StructureKind.MAP) {
            if (consumeEvent() != YamlEvent.BeginStruct) {
                deserializationError("Expect start of structure")
            }

            mapIdx = 0
            return this
        } else if (descriptor.kind == StructureKind.LIST) {
            listIdx = 0
            if (consumeEvent() != YamlEvent.BeginSequence) {
                deserializationError("Expect start of structure")
            }
            return this
        } else if (descriptor.kind == PolymorphicKind.SEALED) {
            if (consumeEvent() != YamlEvent.BeginStruct) {
                deserializationError("Expect start of structure")
            }
            sealedClassLocations.addLast(documentDepth)
            sealedTypeSearch = 0
            return this
        }

        sealedTypeSearch = -1
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
        if (descriptor.kind == StructureKind.MAP) {
            val ev = fetchEvent()
            when (mapIdx) {
                0 -> {
                    if (ev == YamlEvent.EndStruct) return CompositeDecoder.DECODE_DONE
                    else if (ev == null) deserializationError("Expected a value")
                    mapIdx++
                    return 0
                }

                1 -> {
                    if (ev == null) deserializationError("Expected a value")
                    mapIdx = 0
                    return 1
                }
            }
        } else if (descriptor.kind == StructureKind.LIST) {
            val ev = fetchEvent() ?: return CompositeDecoder.DECODE_DONE
            if (ev == YamlEvent.EndSequence) return CompositeDecoder.DECODE_DONE
            return listIdx++
        } else if (sealedTypeSearch >= 0) {
            if (sealedTypeSearch == 1) fetchEvent()
            return sealedTypeSearch // decodeString() will increment this
        }

        while (true) {
            if (didJustCloseStruct) {
                didJustCloseStruct = false
                if (documentDepth == sealedClassLocations.lastOrNull()) {
                    sealedClassLocations.removeLast()
                    return CompositeDecoder.DECODE_DONE
                }
            }

            val ev = fetchEvent() ?: return CompositeDecoder.DECODE_DONE

            if (ev == YamlEvent.EndStruct) {
                didJustCloseStruct = true
                return CompositeDecoder.DECODE_DONE
            }
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

    private fun scanForPolymorphicType(): String? {
        // NOTE(Dan): This function must be invoked immediately after starting the struct which is polymorphic. Thus
        // we can assume that the next event is going to be a key.

        val initialDocDepth = documentDepth
        val initialSeqDepth = sequenceDepth

        val consumedEvents = ArrayList<YamlEvent>()
        consumedEvents.add(YamlEvent.BeginStruct)
        var result: String? = null

        while (result == null) {
            val key = (fetchEvent() ?: break).also { consumedEvents.add(it) }

            val startDocDepth = documentDepth
            val startSeqDepth = sequenceDepth

            val value = (fetchEvent() ?: deserializationError("Expected a value")).also { consumedEvents.add(it) }

            if (documentDepth != startDocDepth) {
                while (documentDepth > startDocDepth) {
                    consumedEvents.add(fetchEvent() ?: break)
                }
            } else if (sequenceDepth != startSeqDepth) {
                while (sequenceDepth > startSeqDepth) {
                    consumedEvents.add(fetchEvent() ?: break)
                }
            }

            if (key is YamlEvent.Scalar && key.value == "type" && value is YamlEvent.Scalar) {
                result = value.value
            }
        }

        eventStack.addAll(consumedEvents)

        documentDepth = initialDocDepth
        sequenceDepth = initialSeqDepth

        return result
    }

    private fun skipCurrentContext() {
        val startDocDepth = documentDepth
        val startSeqDepth = sequenceDepth
        fetchEvent() ?: deserializationError("Unexpected end of document")

        if (documentDepth != startDocDepth) {
            while (documentDepth > startDocDepth) {
                fetchEvent() ?: break
            }
        } else if (sequenceDepth != startSeqDepth) {
            while (sequenceDepth > startSeqDepth) {
                fetchEvent() ?: break
            }
        }
    }

    private val eventStack = ArrayDeque<YamlEvent>()
    private var documentDepth = 0
    private var sequenceDepth = 0
    private fun fetchEvent(): YamlEvent? {
        run {
            val ev = eventStack.removeFirstOrNull()
            lastConsumedToken = ev
            when (ev) {
                is YamlEvent.BeginStruct -> {
                    documentDepth++
                    return ev
                }

                is YamlEvent.EndStruct -> {
                    documentDepth--
                    return ev
                }

                is YamlEvent.EndSequence -> {
                    sequenceDepth--
                    return ev
                }

                else -> {
                    if (ev != null) return ev
                }
            }
        }

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
                    result = YamlEvent.BeginSequence
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
        object BeginSequence : YamlEvent()
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

inline fun <reified T> Yaml.decodeFromString(string: String): T { return decodeFromString(serializer<T>(), string) }

@Serializable
data class ConfigWrapper(
    val foo: Map<String, List<String>>,
    val cfg: MySealedClass
)

@Serializable
sealed class MySealedClass {
    abstract val matches: String
}

@Serializable
@SerialName("Slurm")
data class MySealedSlurm(
    override val matches: String,
    val partition: String,
) : MySealedClass()

@Serializable
@SerialName("Kubernetes")
data class MySealedKubernetes(
    override val matches: String,
    val kubecontext: String,
) : MySealedClass()


fun testYamlParser() {
    Yaml.decodeFromString<ConfigWrapper>(
        """
          cfg:
            matches: something
            partition: fie
            type: Slurm

          foo:
            u1-standard:
            - u1-standard-1
            - u1-standard-2
            - u1-standard-4
            - u1-standard-8
            - u1-standard-16
            - u1-standard-32
            - u1-standard-64
            - u1-standard-special

            u1-foobar:
            - u1-standard-1
            - u1-standard-2
            - u1-standard-4
            - u1-standard-8
            - u1-standard-16
            - u1-standard-32
            - u1-standard-64
            - u1-standard-special

        """.trimIndent()
    ).also { println(it) }
}

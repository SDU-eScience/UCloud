package dk.sdu.cloud.utils

import yaml.*
import kotlinx.cinterop.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.addressOf
import kotlinx.serialization.* import kotlinx.serialization.encoding.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.modules.*

@OptIn(ExperimentalSerializationApi::class)
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

    private var locationTagRequested = -1
    var locationOfLastStructStart: Int = 0
    var approximateLocation: Int = 0

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
        if (locationTagRequested == 3) {
            locationTagRequested = 4
            return approximateLocation
        }

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
        locationOfLastStructStart = approximateLocation
        if (descriptor.serialName.endsWith("YamlLocationTag")) {
            locationTagRequested = 3
            return this
        } else if (descriptor.kind == StructureKind.MAP) {
            if (consumeEvent() !is YamlEvent.BeginStruct) {
                deserializationError("Expect start of structure")
            }

            mapIdx = 0
            return this
        } else if (descriptor.kind == StructureKind.LIST) {
            listIdx = 0
            if (consumeEvent() !is YamlEvent.BeginSequence) {
                deserializationError("Expect start of structure")
            }
            return this
        } else if (descriptor.kind == PolymorphicKind.SEALED) {
            if (consumeEvent() !is YamlEvent.BeginStruct) {
                deserializationError("Expect start of structure")
            }
            sealedClassLocations.addLast(documentDepth)
            sealedTypeSearch = 0
            return this
        }

        sealedTypeSearch = -1
        if (descriptor.kind != StructureKind.CLASS) return this

        for (e in descriptor.elementDescriptors) {
            if (e.serialName.endsWith("YamlLocationTag")) {
                locationTagRequested = 1
                break
            }
        }

        if (consumeEvent() !is YamlEvent.BeginStruct) {
            deserializationError("Expect start of structure")
        }
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (locationTagRequested > 0) {
            locationTagRequested = -1
            return
        }

        if (descriptor.kind != StructureKind.CLASS) return
        if (consumeEvent() !is YamlEvent.EndStruct) {
            deserializationError("Expect end of structure")
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (descriptor.kind == StructureKind.MAP) {
            val ev = fetchEvent()
            when (mapIdx) {
                0 -> {
                    if (ev is YamlEvent.EndStruct) return CompositeDecoder.DECODE_DONE
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
            if (ev is YamlEvent.EndSequence) return CompositeDecoder.DECODE_DONE
            return listIdx++
        } else if (sealedTypeSearch >= 0) {
            if (sealedTypeSearch == 1) fetchEvent()
            return sealedTypeSearch // decodeString() will increment this
        }

        if (locationTagRequested == 1) {
            for ((index, e) in descriptor.elementDescriptors.withIndex()) {
                if (e.serialName.endsWith("YamlLocationTag")) {
                    locationTagRequested = 2
                    return index
                }
            }
        } else if (locationTagRequested == 3) {
            return 0
        } else if (locationTagRequested == 4) {
            return CompositeDecoder.DECODE_DONE
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

            if (ev is YamlEvent.EndStruct) {
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
        throw YamlException(message, YamlLocationReference(locationOfLastStructStart, approximateLocation))
    }

    private var lastConsumedToken: YamlEvent? = null
    private fun consumeEvent(): YamlEvent {
        if (firstEvent) {
            fetchEvent()
            firstEvent = false
            return consumeEvent()
        }
        if (lastConsumedToken == null) {
            try {
                error("BAD")
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }
        val result = lastConsumedToken ?: error("lastConsumedToken should not be null")
        approximateLocation = result.location
        lastConsumedToken = null
        return result
    }

    private fun scanForPolymorphicType(): String? {
        // NOTE(Dan): This function must be invoked immediately after starting the struct which is polymorphic. Thus
        // we can assume that the next event is going to be a key.

        val initialDocDepth = documentDepth
        val initialSeqDepth = sequenceDepth

        val consumedEvents = ArrayList<YamlEvent>()
        consumedEvents.add(YamlEvent.BeginStruct(approximateLocation))
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
                val yamlProblem = parser.problem?.toKString() ?: "Unknown parsing error"
                throw YamlException(
                    yamlProblem, 
                    YamlLocationReference(locationOfLastStructStart, parser.problem_mark.index.toInt())
                )
            }

            val location = parser.mark.index.toInt()

            when (event.type) {
                yaml_event_type_e.YAML_SCALAR_EVENT -> {
                    val ev = event.data.scalar
                    val value = ev.value?.readBytes(ev.length.toInt())?.toKString() ?: ""
                    val isQuoted = ev.quoted_implicit
                    result = YamlEvent.Scalar(value, isQuoted == 1, location)
                }

                yaml_event_type_e.YAML_MAPPING_START_EVENT -> {
                    result = YamlEvent.BeginStruct(location)
                    documentDepth++
                }

                yaml_event_type_e.YAML_MAPPING_END_EVENT -> {
                    result = YamlEvent.EndStruct(location)
                    documentDepth--
                }

                yaml_event_type_e.YAML_SEQUENCE_START_EVENT -> {
                    result = YamlEvent.BeginSequence(location)
                    sequenceDepth++
                }

                yaml_event_type_e.YAML_SEQUENCE_END_EVENT -> {
                    result = YamlEvent.EndSequence(location)
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
        abstract val location: Int

        data class Scalar(val value: String, val quoted: Boolean, override val location: Int) : YamlEvent()
        data class BeginStruct(override val location: Int) : YamlEvent()
        data class EndStruct(override val location: Int) : YamlEvent()
        data class BeginSequence(override val location: Int) : YamlEvent()
        data class EndSequence(override val location: Int) : YamlEvent()
    }
}

object Yaml {
    fun <T> decodeFromString(
        deserializer: DeserializationStrategy<T>,
        string: String,
        locationRef: MutableRef<YamlLocationReference>? = null,
    ): T {
        memScoped {
            val document = string.encodeToByteArray().toUByteArray().pin()
            defer { document.unpin() }

            val parser = alloc<yaml_parser_t>()
            yaml_parser_initialize(parser.ptr)
            yaml_parser_set_input_string(parser.ptr, document.addressOf(0), document.get().size.toULong())
            defer { yaml_parser_delete(parser.ptr) }

            val decoder = YamlDecoder(parser)
            return try {
                decoder.decodeSerializableValue(deserializer)
            } finally {
                if (locationRef != null) {
                    locationRef.value = YamlLocationReference(
                        decoder.locationOfLastStructStart,
                        decoder.approximateLocation
                    )
                }
            }
        }
    }
}

inline fun <reified T> Yaml.decodeFromString(string: String): T { return decodeFromString(serializer<T>(), string) }

data class YamlLocationReference(val approximateStart: Int = 0, val approximateEnd: Int = 0)
class YamlException(message: String, val location: YamlLocationReference) : RuntimeException(message)

@Serializable
data class YamlLocationTag(
    val offset: Int
) {
    fun toReference(): YamlLocationReference = YamlLocationReference(offset, offset)
}

@Serializable(with = YamlStringSerializer::class)
data class YamlString(
    val tag: YamlLocationTag,
    val value: String,
) {
    override fun toString() = value
}

object YamlStringSerializer : KSerializer<YamlString> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("YamlString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: YamlString) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): YamlString {
        val yamlDecoder = decoder as? YamlDecoder
        val tag = if (yamlDecoder != null) YamlLocationTag(yamlDecoder.approximateLocation) else YamlLocationTag(0)
        val value = decoder.decodeString()
        return YamlString(tag, value)
    }
}

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

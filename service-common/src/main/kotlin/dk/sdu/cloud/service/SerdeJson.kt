package dk.sdu.cloud.service

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

// Code is adapted from: https://github.com/apache/kafka/blob/c504b22841b1066c0d46183adb22f48596479d7e/
//                       streams/examples/src/main/java/org/apache/service/streams/examples/pageview/
//                       PageViewTypedDemo.java
//
// It has been modified to work better with kotlin

const val SERIALIZER_POJO_CLASS = "JsonPOJOClass"
const val SERIALIZER_TYPE_REF = "JsonPOJOTypeRef"
const val SERIALIZER_JAVATYPE_REF = "JsonPOJOJavaTypeRef"

private val objectMapper by lazy {
    jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        configure(JsonParser.Feature.ALLOW_COMMENTS, true)
        configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
        configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
    }
}

class JsonPOJODeserializer<T> : Deserializer<T> {
    private var typeReference: TypeReference<T>? = null
    private var klass: Class<T>? = null
    private var javaType: JavaType? = null

    @Suppress("UNCHECKED_CAST")
    override fun configure(props: Map<String, *>, isKey: Boolean) {
        klass = props[SERIALIZER_POJO_CLASS] as? Class<T>
        typeReference = props[SERIALIZER_TYPE_REF] as? TypeReference<T>
        javaType = props[SERIALIZER_JAVATYPE_REF] as? JavaType
    }

    override fun deserialize(topic: String, bytes: ByteArray?): T? {
        if (bytes == null) return null

        return try {
            when {
                javaType != null -> objectMapper.readValue(bytes, javaType)
                typeReference != null -> objectMapper.readValue(bytes, typeReference)
                klass != null -> objectMapper.readValue(bytes, klass)
                else -> throw IllegalStateException("Serde is not configured. No type reference or class reference")
            }
        } catch (e: Exception) {
            throw SerializationException(e)
        }
    }

    override fun close() { /* empty */
    }
}

class JsonPOJOSerializer<T> : Serializer<T> {
    private var writer: ObjectWriter = objectMapper.writer()

    override fun configure(props: Map<String, *>, isKey: Boolean) {
        @Suppress("UNCHECKED_CAST")
        val typeReference = props[SERIALIZER_TYPE_REF] as? TypeReference<T>?

        writer = objectMapper.writerFor(typeReference)
    }

    override fun serialize(topic: String, data: T?): ByteArray? {
        if (data == null) return null

        try {
            return writer.writeValueAsBytes(data)
        } catch (e: Exception) {
            throw SerializationException("Error serializing JSON message", e)
        }
    }

    override fun close() { /* empty */
    }
}

object JsonSerde {
    /**
     * This property is only public to allow for easy-inlining. Should not be used directly
     */
    val cachedSerdes = HashMap<Any, Serde<*>>()

    inline fun <reified Type> createJsonSerializers(): Pair<Serializer<Type>, Deserializer<Type>> {
        return createJsonSerializersFromClass(Type::class.java)
    }

    fun <Type> createJsonSerializersFromClass(klass: Class<Type>): Pair<Serializer<Type>, Deserializer<Type>> {
        val deserializer: Deserializer<Type> = JsonPOJODeserializer()
        val props = mapOf(SERIALIZER_POJO_CLASS to klass)
        deserializer.configure(props, false)

        val serializer: Serializer<Type> = JsonPOJOSerializer()
        serializer.configure(props, false)
        return Pair(serializer, deserializer)
    }

    fun <Type> createJsonSerializersFromTypeRef(ref: TypeReference<Type>):
            Pair<Serializer<Type>, Deserializer<Type>> {
        val deserializer: Deserializer<Type> = JsonPOJODeserializer()
        val props = mapOf(SERIALIZER_TYPE_REF to ref)
        deserializer.configure(props, false)

        val serializer: Serializer<Type> = JsonPOJOSerializer()
        serializer.configure(props, false)
        return Pair(serializer, deserializer)
    }

    fun <Type> createJsonSerializersFromJavaType(ref: JavaType): Pair<Serializer<Type>, Deserializer<Type>> {
        val deserializer: Deserializer<Type> = JsonPOJODeserializer()
        val props = mapOf(SERIALIZER_JAVATYPE_REF to ref)
        deserializer.configure(props, false)

        val serializer: Serializer<Type> = JsonPOJOSerializer()
        serializer.configure(props, false)
        return Pair(serializer, deserializer)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> jsonSerdeFromClass(klass: Class<T>): Serde<T> {
        val existing = cachedSerdes[klass]
        if (existing != null) return existing as Serde<T>
        val (serializer, deserializer) = createJsonSerializersFromClass(klass)
        val newSerde = Serdes.serdeFrom(serializer, deserializer)
        cachedSerdes[klass] = newSerde
        return newSerde
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> jsonSerdeFromTypeRef(ref: TypeReference<T>): Serde<T> {
        val existing = cachedSerdes[ref]
        if (existing != null) return existing as Serde<T>
        val (serializer, deserializer) = createJsonSerializersFromTypeRef(ref)
        val newSerde = Serdes.serdeFrom(serializer, deserializer)
        cachedSerdes[ref] = newSerde
        return newSerde
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> jsonSerdeFromJavaType(ref: JavaType): Serde<T> {
        val existing = cachedSerdes[ref]
        if (existing != null) return existing as Serde<T>
        val (serializer, deserializer) = createJsonSerializersFromJavaType<T>(ref)
        val newSerde = Serdes.serdeFrom(serializer, deserializer)
        cachedSerdes[ref] = newSerde
        return newSerde
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified Type : Any> jsonSerde(): Serde<Type> {
        return jsonSerdeFromTypeRef(jacksonTypeRef())
    }
}

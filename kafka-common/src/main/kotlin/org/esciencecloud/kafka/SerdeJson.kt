package org.esciencecloud.kafka

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

// Code is adapted from: https://github.com/apache/kafka/blob/c504b22841b1066c0d46183adb22f48596479d7e/
//                       streams/examples/src/main/java/org/apache/kafka/streams/examples/pageview/
//                       PageViewTypedDemo.java
//
// It has been modified to work better with kotlin

const val SERIALIZER_POJO_CLASS = "JsonPOJOClass"
private val objectMapper by lazy {
    jacksonObjectMapper().apply {
        configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    }
}

class JsonPOJODeserializer<T> : Deserializer<T> {
    private lateinit var tClass: Class<T>

    override fun configure(props: Map<String, *>, isKey: Boolean) {
        @Suppress("UNCHECKED_CAST")
        tClass = props[SERIALIZER_POJO_CLASS] as Class<T>
    }

    override fun deserialize(topic: String, bytes: ByteArray?): T? {
        if (bytes == null) {
            return null
        }

        val data: T
        try {
            data = objectMapper.readValue(bytes, tClass)
        } catch (e: Exception) {
            throw SerializationException(e)
        }

        return data
    }

    override fun close() { /* empty */
    }
}

class JsonPOJOSerializer<T> : Serializer<T> {
    override fun configure(props: Map<String, *>, isKey: Boolean) {}

    override fun serialize(topic: String, data: T?): ByteArray? {
        if (data == null) {
            return null
        }

        try {
            return objectMapper.writeValueAsBytes(data)
        } catch (e: Exception) {
            throw SerializationException("Error serializing JSON message", e)
        }

    }

    override fun close() { /* empty */ }
}

object JsonSerde {
    /**
     * This property is only public to allow for easy-inlining. Should not be used directly
     */
    val cachedSerdes = HashMap<Class<*>, Serde<*>>()

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

    @Suppress("UNCHECKED_CAST")
    fun <T> jsonSerdeFromClass(klass: Class<T>): Serde<T> {
        val existing = JsonSerde.cachedSerdes[klass]
        if (existing != null) return existing as Serde<T>
        val (serializer, deserializer) = createJsonSerializersFromClass(klass)
        val newSerde = Serdes.serdeFrom(serializer, deserializer)
        JsonSerde.cachedSerdes[klass] = newSerde
        return newSerde
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified Type> jsonSerde(): Serde<Type> {
        return jsonSerdeFromClass(Type::class.java)
    }

}

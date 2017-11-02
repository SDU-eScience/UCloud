package org.esciencecloud.storage.server

import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.UserType

// Potential candidates for inclusion in kafka-common if they prove useful
const val REQUEST = "request"
const val RESPONSE = "response"

@Suppress("MemberVisibilityCanPrivate")
class RequestResponseStream<KeyType : Any, RequestType : Any>(
        topicName: String,
        keySerde: Serde<KeyType>,
        requestSerde: Serde<Request<RequestType>>,
        responseSerde: Serde<Response<RequestType>>
) {
    val requestStream = StreamDescription(
            "$REQUEST.$topicName",
            keySerde,
            requestSerde
    )

    val responseStream = StreamDescription(
            "$RESPONSE.$topicName",
            Serdes.String(),
            responseSerde
    )

    private fun <OutputKey : Any> map(
            builder: KStreamBuilder,
            outputKeySerde: Serde<OutputKey>,
            mapper: (KeyType, Request<RequestType>) -> Pair<OutputKey, Response<RequestType>>
    ) {
        requestStream.stream(builder).map { key, value ->
            val (a, b) = mapper(key, value)
            KeyValue.pair(a, b)
        }.to(outputKeySerde, responseStream.valueSerde, responseStream.name)
    }

    fun process(builder: KStreamBuilder, mapper: (KeyType, Request<RequestType>) -> (Result<*>)) {
        return map(builder, Serdes.String()) { key, request ->
            Pair(request.header.uuid, mapper(key, request).toResponse(request))
        }
    }

    companion object {
        inline fun <reified KeyType : Any, reified RequestType : Any> create(
                topicName: String
        ): RequestResponseStream<KeyType, RequestType> {
            // TODO FIXME RIGHT HERE. THERE IS SOME GENERIC ERASURE ON THE EVENT-TYPE
            return RequestResponseStream(topicName, jsonSerde<KeyType>(), jsonSerde<Request<RequestType>>(), jsonSerde<Response<RequestType>>())
        }
    }
}

fun main(args: Array<String>) {
    val serde: Serde<Request<UserEvent>> = jsonSerde()
    val createRequest = Request(
            RequestHeader("uuid", ProxyClient("username", "password")),
            UserEvent.Create("123", null, UserType.USER)
    )
    val serialized = serde.serializer().serialize("f", createRequest)
    val message = serialized.toString(Charsets.UTF_8)
    println(message)

    val deserialized = serde.deserializer().deserialize("f", message.toByteArray())
    println(deserialized)
}

fun KafkaStreams.addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread { this.close() })
}

package org.esciencecloud.storage.server

import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.storage.Result

// Potential candidates for inclusion in kafka-common if they prove useful
const val REQUEST = "request"
const val RESPONSE = "response"
const val POLICY = "policy"

@Suppress("MemberVisibilityCanPrivate")
class RequestResponseStream<RequestType : Any>(
        topicName: String,
        requestSerde: Serde<RequestType>,
        responseSerde: Serde<StorageResponse<RequestType>>
) {
    val requestStream = StreamDescription(
            "$REQUEST.$topicName",
            Serdes.String(),
            requestSerde
    )

    val responseStream = StreamDescription(
            "$RESPONSE.$topicName",
            Serdes.String(),
            responseSerde
    )

    fun map(
            builder: KStreamBuilder,
            mapper: (String, RequestType) -> Pair<String, StorageResponse<RequestType>>
    ) {
        requestStream.stream(builder).map { key, value ->
            val (a, b) = mapper(key, value)
            KeyValue.pair(a, b)
        }.to(responseStream.keySerde, responseStream.valueSerde, responseStream.name)
    }

    fun mapResult(builder: KStreamBuilder, mapper: (RequestType) -> (Result<*>)) {
        return map(builder) { key, request ->
            Pair(key, mapper(request).toResponse(request))
        }
    }

    companion object {
        inline fun <reified RequestType : Any> create(topicName: String): RequestResponseStream<RequestType> {
            return RequestResponseStream(topicName, jsonSerde(), jsonSerde())
        }
    }
}

fun <Req : Any> KStream<String, StorageResponse<Req>>.toDescription(description: RequestResponseStream<Req>) {
    this.toDescription(description.responseStream)
}

fun <Key, Value> KStream<Key, Value>.toDescription(streamDescription: StreamDescription<Key, Value>) {
    this.to(streamDescription.keySerde, streamDescription.valueSerde, streamDescription.name)
}

fun KafkaStreams.addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread { this.close() })
}

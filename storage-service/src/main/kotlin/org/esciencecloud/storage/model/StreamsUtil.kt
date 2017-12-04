package org.esciencecloud.storage.model

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result

// Potential candidates for inclusion in kafka-common if they prove useful
const val REQUEST = "request"
const val RESPONSE = "response"

@Suppress("MemberVisibilityCanPrivate")
class RequestResponseStream<KeyType : Any, RequestType : Any>(
        topicName: String,
        private val keySerde: Serde<KeyType>,
        private val requestSerde: Serde<Request<RequestType>>,
        private val responseSerde: Serde<Response<RequestType>>
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

    fun produceRequest(producer: KafkaProducer<String, String>,
                       key: KeyType, request: Request<RequestType>,
                       callback: ((RecordMetadata, Exception?) -> Unit)? = null) {
        val topic = requestStream.name
        producer.send(ProducerRecord(
                topic,
                keySerde.serializer().serialize(topic, key).toString(Charsets.UTF_8),
                requestSerde.serializer().serialize(topic, request).toString(Charsets.UTF_8)
        ), callback)
    }

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
}

private fun <T : Any, InputType : Any> Result<T>.toResponse(input: Request<InputType>) =
        Response(this is Ok, (this as? Error)?.message, input)


fun KafkaStreams.addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread { this.close() })
}

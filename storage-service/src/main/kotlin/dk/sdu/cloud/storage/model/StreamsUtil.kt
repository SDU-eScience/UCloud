package dk.sdu.cloud.storage.model

import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.StreamDescription
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Result
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Produced

// Potential candidates for inclusion in kafka-common if they prove useful
// TODO This is potentially outdated
const val REQUEST = "request"
const val RESPONSE = "response"

@Suppress("MemberVisibilityCanPrivate")
class RequestResponseStream<KeyType : Any, RequestType : Any>(
        topicName: String,
        private val keySerde: Serde<KeyType>,
        private val requestSerde: Serde<KafkaRequest<RequestType>>,
        private val responseSerde: Serde<Response<RequestType>>
) {
    val requestStream = StreamDescription(
            "${REQUEST}.$topicName",
            keySerde,
            requestSerde
    )

    val responseStream = StreamDescription(
            "${RESPONSE}.$topicName",
            Serdes.String(),
            responseSerde
    )

    fun produceRequest(producer: KafkaProducer<String, String>,
                       key: KeyType, request: KafkaRequest<RequestType>,
                       callback: ((RecordMetadata, Exception?) -> Unit)? = null) {
        val topic = requestStream.name
        producer.send(ProducerRecord(
                topic,
                keySerde.serializer().serialize(topic, key).toString(Charsets.UTF_8),
                requestSerde.serializer().serialize(topic, request).toString(Charsets.UTF_8)
        ), callback)
    }

    private fun <OutputKey : Any> map(
            builder: StreamsBuilder,
            outputKeySerde: Serde<OutputKey>,
            mapper: (KeyType, KafkaRequest<RequestType>) -> Pair<OutputKey, Response<RequestType>>
    ) {
        requestStream.stream(builder).map { key, value ->
            val (a, b) = mapper(key, value)
            KeyValue.pair(a, b)
        }.to(responseStream.name, Produced.with(outputKeySerde, responseStream.valueSerde))
    }

    fun process(builder: StreamsBuilder, mapper: (KeyType, KafkaRequest<RequestType>) -> (Result<*>)) {
        return map(builder, Serdes.String()) { key, request ->
            Pair(request.header.uuid, mapper(key, request).toResponse(request))
        }
    }
}

private fun <T : Any, InputType : Any> Result<T>.toResponse(input: KafkaRequest<InputType>) =
        Response(this is Ok, (this as? Error)?.message, input)


fun KafkaStreams.addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread { this.close() })
}

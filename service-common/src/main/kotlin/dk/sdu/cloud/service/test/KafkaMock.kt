package dk.sdu.cloud.service.test

import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.StreamDescription
import io.mockk.coEvery
import io.mockk.mockk
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

object KafkaMock {
    lateinit var mockedKafkaProducer: KafkaProducer<String, String>
        private set

    lateinit var mockedAdminClient: AdminClient
        private set

    val recordedProducerRecords = ArrayList<ProducerRecord<String, String>>()

    fun initialize(): KafkaServices {
        reset()

        // Create mocks
        val kafkaProducer: KafkaProducer<String, String> = mockk(relaxed = true)
        val adminClient: AdminClient = mockk(relaxed = true)
        this.mockedKafkaProducer = kafkaProducer
        this.mockedAdminClient = adminClient


        val recordMetadata = mockk<RecordMetadata>(relaxed = true)
        val mockedResponse: Future<RecordMetadata> = CompletableFuture.completedFuture(recordMetadata)
        coEvery { kafkaProducer.send(capture(recordedProducerRecords), any()) } answers {
            val callback = invocation.args[1] as Callback
            callback.onCompletion(recordMetadata, null)
            mockedResponse
        }

        return KafkaServices(
            streamsConfig = Properties(),
            consumerConfig = Properties(),
            producer = kafkaProducer,
            adminClient = adminClient
        )
    }


    fun reset() {
        recordedProducerRecords.clear()
    }
}


fun <Key, Value> KafkaMock.assertKVMessage(topic: StreamDescription<Key, Value>, key: Key, value: Value) {
    assert(recordedProducerRecords.any {
        it.topic() == topic.name &&
                it.key() == topic.keySerde.serializer().serialize(topic.name, key).toString(Charsets.UTF_8) &&
                it.value() == topic.valueSerde.serializer().serialize(topic.name, value).toString(Charsets.UTF_8)
    })
}

fun <Value> KafkaMock.assertMessage(topic: StreamDescription<*, Value>, value: Value) {
    assert(recordedProducerRecords.any {
        it.topic() == topic.name &&
                it.value() == topic.valueSerde.serializer().serialize(topic.name, value).toString(Charsets.UTF_8)
    })
}

fun <Key, Value> KafkaMock.messagesForTopic(topic: StreamDescription<Key, Value>): List<Pair<Key, Value>> {
    return recordedProducerRecords.filter { it.topic() == topic.name }.map {
        Pair(
            topic.keySerde.deserializer().deserialize(topic.name, it.key().toByteArray(Charsets.UTF_8)),
            topic.valueSerde.deserializer().deserialize(topic.name, it.value().toByteArray(Charsets.UTF_8))
        )
    }
}

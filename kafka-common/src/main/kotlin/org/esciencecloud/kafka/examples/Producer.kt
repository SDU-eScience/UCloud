package org.esciencecloud.kafka.examples

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord

const val N = 100

fun main(args: Array<String>) {
    val producer = KafkaProducer<String, JsonNode>(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.connect.json.JsonSerializer"
    ))

    val mapper = jacksonObjectMapper()
    mapper.propertyNamingStrategy = PropertyNamingStrategy.UPPER_CAMEL_CASE

    (0..N).forEach {
        val key = "Message-$it"
        val message = DummyMessage(key, it.toLong(), it % 2 == 0)
        val node: JsonNode = mapper.valueToTree(message)
        val record = ProducerRecord(TOPIC, key, node)
        producer.send(record)
    }
    producer.flush()
    producer.close()
}

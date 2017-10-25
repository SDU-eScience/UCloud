package org.esciencecloud.kafka.examples

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Consumer")!!

fun main(args: Array<String>) {
    val kafkaHost = "localhost:9092"
    val consumer = KafkaConsumer<String, JsonNode>(mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaHost,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringDeserializer",
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to "org.apache.kafka.connect.json.JsonDeserializer",

            // Group ID is mandatory. It tells Kafka which type of consumer we are. Messages are essentially
            // load-balanced within groups
            ConsumerConfig.GROUP_ID_CONFIG to "demo-consumer",

            // Client IDs are not mandatory. They tell Kafka which consumer this specific instance is within the
            // group.
            ConsumerConfig.CLIENT_ID_CONFIG to "1"
    ))

    // Jackson serializer likes changing the case around. This is more a work-around than anything else
    val mapper = jacksonObjectMapper()
    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)

    consumer.subscribe(listOf(TOPIC))
    while (true) {
        val poll = consumer.poll(1000)
        log.info("Found ${poll.count()} results")
        poll.forEach {
            try {
                val message = mapper.treeToValue(it.value(), DummyMessage::class.java)
                log.info(message.toString())
            } catch (ex: Exception) {
                log.warn("Unable to parse message! ${ex.message}")
            }
        }
    }
}
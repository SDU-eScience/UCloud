package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

object KafkaReconnectionStreams : KafkaDescriptions() {
    val testStream = stream<String, String>("kafka-reconnection-issue") { UUID.randomUUID().toString() }
}

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("KafkaReconnectionIssue")

    val description = object : ServiceDescription {
        override val name: String = "issue-315"
        override val version: String = "1.0.0"
    }

    val micro = Micro().apply {
        init(description, args)
        installDefaultFeatures(kafkaTopicConfig = KafkaTopicFeatureConfiguration(basePackages = listOf("dk.sdu.cloud.service")))
    }

    val kafka = micro.kafka

    runBlocking {
        log.info("Starting producer thread")
        val producerJob = launch {
            val producer = kafka.producer.forStream(KafkaReconnectionStreams.testStream)
            while (true) {
                try {
                    producer.emit("New event at ${Date(System.currentTimeMillis())}")
                    delay(1, TimeUnit.SECONDS)
                } catch (ex: Exception) {
                    log.info("Exception caught in producer thread")
                    log.info(ex.stackTraceToString())
                }
            }
        }

        log.info("Starting consumer")
        val consumer = kafka.createConsumer(KafkaReconnectionStreams.testStream)
            .configure { root ->
                root.consumeAndCommit {
                    println("Consuming new event: $it")
                }
            }

        producerJob.join()
        consumer.close()
    }
}

package dk.sdu.cloud.storage.processor

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import java.util.*

class StorageStreamProcessor(private val storageService: StorageService, private val kafkaConfig: KafkaConfiguration) {
    private val userProcessor = Users(storageService)
    private val groupProcessor = Groups(storageService)
    private val acProcessor = AccessControl(storageService)

    fun retrieveKafkaConfiguration(): Properties {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.servers.joinToString(",")
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        return properties
    }

    fun constructStreams(builder: StreamsBuilder) {
        userProcessor.initStream(builder)
        acProcessor.initStream(builder)
        groupProcessor.initStream(builder)

        // TODO How will we have other internal systems do work here? They won't have a performed by when the task
        // is entirely internal to the system. This will probably just be some simple API token such that we can confirm
        // who is performing the request.
    }
}

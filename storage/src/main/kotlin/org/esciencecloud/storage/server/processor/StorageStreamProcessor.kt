package org.esciencecloud.storage.server.processor

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import java.util.*

class StorageStreamProcessor(private val storageService: StorageService) {
    private val userProcessor = Users(storageService)
    private val acProcessor = AccessControl(storageService)

    fun retrieveKafkaConfiguration(): Properties {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092" // Comma-separated. Should point to at least 3
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        return properties
    }

    fun constructStreams(builder: KStreamBuilder) {
        userProcessor.init(builder)
        acProcessor.initStream(builder)

        // TODO How will we have other internal systems do work here? They won't have a performed by when the task
        // is entirely internal to the system. This will probably just be some simple API token such that we can confirm
        // who is performing the request.
    }
}

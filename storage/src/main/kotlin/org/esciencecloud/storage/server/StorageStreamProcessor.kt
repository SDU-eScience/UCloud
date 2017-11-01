package org.esciencecloud.storage.server

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import java.util.*

class StorageStreamProcessor(private val storageService: StorageService) {
    private val ugProcessor = UserGroupsStreamProcessor(storageService)
    private val acProcessor = AccessControlStreamProcessor(storageService)

    fun retrieveKafkaConfiguration(): Properties {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092" // Comma-separated. Should point to at least 3
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        return properties
    }

    fun constructStreams(builder: KStreamBuilder) {
        ugProcessor.init(builder)
        acProcessor.init(builder)

        // TODO FIXME THIS SHOULD BE REMOVED LATER
        // TODO FIXME THIS SHOULD BE REMOVED LATER
        UserGroupsProcessor.Bomb.mapResult(builder) {
            // The idea is that we use this to test handling of jobs that are causing consistent crashes with the
            // system. We should be able to handle these without killing the entire system.
            throw RuntimeException("Boom!")
        }
        // TODO FIXME THIS SHOULD BE REMOVED LATER
        // TODO FIXME THIS SHOULD BE REMOVED LATER

        // TODO How will we have other internal systems do work here? They won't have a performed by when the task
        // is entirely internal to the system. This will probably just be some simple API token such that we can confirm
        // who is performing the request.
    }
}

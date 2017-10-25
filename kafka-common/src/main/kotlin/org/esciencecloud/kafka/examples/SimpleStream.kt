package org.esciencecloud.kafka.examples

import org.esciencecloud.kafka.gateway.PermissionRequest
import org.esciencecloud.kafka.jsonSerde
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import java.io.File
import java.util.*

fun main(args: Array<String>) {
    val properties = Properties()
    properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
    properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092" // Comma-separated. Should point to at least 3
    properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events

    val builder = KStreamBuilder()
    val permissionRequests = builder.stream(Serdes.String(), jsonSerde<PermissionRequest>(), "bulk-permission-request")

    val output = File("output.txt").printWriter()

    //
    // According to this[1] post, you should just call your external systems in a sync manner from within the map
    // call. I am, however, concerned about how this will scale. But maybe if we just run more instances of this
    // application we will be fine? I don't know. We will need to check out if this is a non-issue.
    //
    // [1]: https://stackoverflow.com/questions/42064430/external-system-queries-during-kafka-stream-processing
    //
    permissionRequests.map { key, value ->
        output.println("Doing some work for $key $value")
        output.flush()
        KeyValue(key, value)
    }.to(Serdes.String(), jsonSerde<PermissionRequest>(), "bulk-permission-responses")

    val streams = KafkaStreams(builder, properties)
    streams.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        output.close()
        streams.close()
    })
}

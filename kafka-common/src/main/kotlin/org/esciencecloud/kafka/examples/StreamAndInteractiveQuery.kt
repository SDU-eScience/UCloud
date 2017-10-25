package org.esciencecloud.kafka.examples

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.state.HostInfo
import org.esciencecloud.kafka.lookupKafkaStoreOrProxy
import org.esciencecloud.kafka.queryParamOrBad
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.routing.routing
import java.util.*

fun main(args: Array<String>) {
    // To allow for multiple we should have these be dynamic from arguments (or elsewhere)
    MetadataProcessorApp("localhost", 42001).start()
}

class MetadataProcessorApp(val hostname: String, val port: Int) {
    private val thisHost = HostInfo(hostname, port)

    fun start() {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "metadata-processor"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092" // Comma-separated. Should point to at least 3
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events

        // Matches the RPC endpoint
        properties[StreamsConfig.APPLICATION_SERVER_CONFIG] = "$hostname:$port"


        val builder = KStreamBuilder()
        MetadataProcessor.UpdateStream.groupByKey(builder).aggregate(
                { null },
                { _, newValue, _ -> newValue },
                MetadataProcessor.MaterializedTable.valueSerde,
                MetadataProcessor.MaterializedTable.name
        )

        // Purging all is a bit problematic since Kafka doesn't know that there is a hierarchy structure hidden
        // within the key

        val streams = KafkaStreams(builder, properties)
        streams.start()

        Runtime.getRuntime().addShutdownHook(Thread { streams.close() })

        createRestServer(streams).start(wait = true)
    }

    // The rest interface functions as an RPC layer amongst instances of same application.
    // However, it can also function as an endpoint for other services to use (for example, to query about the
    // current state).
    private fun createRestServer(streams: KafkaStreams) = embeddedServer(Netty, port = port) {
        install(GsonSupport)

        routing {
            get("/metadata/") {
                val path = queryParamOrBad("path") ?: return@get
                val metaKey = queryParamOrBad("key") ?: return@get

                val kafkaKey = MetadataKey(path, metaKey)
                lookupKafkaStoreOrProxy(streams, thisHost, MetadataProcessor.MaterializedTable, kafkaKey)
            }
        }
    }
}


package dk.sdu.cloud.service

import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.concurrent.TimeUnit

interface BaseServer {
    fun start()
    fun stop()
}

interface CommonServer : BaseServer, Loggable {
    val httpServer: ApplicationEngine?
    val kafka: KafkaServices
    val kStreams: KafkaStreams?

    override fun stop() {
        httpServer?.stop(gracePeriod = 0, timeout = 30, timeUnit = TimeUnit.SECONDS)
        kStreams?.close(30, TimeUnit.SECONDS)
    }
}

fun CommonServer.buildStreams(builder: (StreamsBuilder) -> Unit): KafkaStreams {
    log.info("Configuring stream processors...")
    val kBuilder = StreamsBuilder()
    builder(kBuilder)
    return kafka.build(kBuilder.build()).also {
        it.setUncaughtExceptionHandler { _, exception ->
            log.error("Caught fatal exception in Kafka! Stacktrace follows:")
            log.error(exception.stackTraceToString())
            stop()
        }
        log.info("Stream processors configured!")
    }
}

fun CommonServer.startServices() {
    val httpServer = httpServer
    if (httpServer != null) {
        log.info("Starting HTTP server...")
        httpServer.start(wait = false)
        log.info("HTTP server started!")
    }

    val kStreams = kStreams
    if (kStreams != null) {
        log.info("Starting Kafka Streams...")
        kStreams.start()
        log.info("Kafka Streams started!")
    }
}

package dk.sdu.cloud.service

import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.concurrent.TimeUnit

interface BaseServer {
    fun start()
    fun stop()
}

private const val TIMEOUT_DEFAULT = 30L

interface CommonServer : BaseServer, Loggable {
    val httpServer: ApplicationEngine?
    val kafka: KafkaServices
    val kStreams: KafkaStreams?

    override fun stop() {
        httpServer?.stop(gracePeriod = 0, timeout = TIMEOUT_DEFAULT, timeUnit = TimeUnit.SECONDS)
        kStreams?.close(TIMEOUT_DEFAULT, TimeUnit.SECONDS)
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
    val kStreams = kStreams
    if (kStreams != null) {
        log.info("Starting Kafka Streams...")
        kStreams.start()
        log.info("Kafka Streams started!")
    }

    val httpServer = httpServer
    if (httpServer != null) {
        log.info("Starting HTTP server...")
        httpServer.start(wait = true)
        log.info("HTTP server started!")
    }
}

fun EventConsumer<*>.installShutdownHandler(server: CommonServer) {
    with(server) {
        onExceptionCaught { ex ->
            log.warn("Caught fatal exception in event consumer!")
            log.warn(ex.stackTraceToString())
            server.stop()
        }
    }
}

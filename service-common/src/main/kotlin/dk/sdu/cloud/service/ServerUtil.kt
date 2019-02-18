package dk.sdu.cloud.service

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.kafka
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.time.Duration
import java.time.temporal.ChronoUnit

interface BaseServer {
    fun start()
    fun stop()
}

private const val STOP_TIMEOUT = 30L

/**
 * An interface for a normal servers bootstrapped by [Micro].
 *
 * It provides utility functions for starting and stopping services. Services can be started with [startServices] and
 * stopped with [stopServices]. The underlying services that are stopped depend on which additional services are
 * required. The [CommonServer] supports the following additional interfaces:
 *
 * - [WithKafkaStreams]
 */
interface CommonServer : BaseServer, Loggable {
    val micro: Micro

    override fun stop() {
        stopServices()
    }
}

/**
 * An interface to be used in combination with [CommonServer]
 *
 * Streams can be built using [buildStreams].
 */
interface WithKafkaStreams {
    var kStreams: KafkaStreams
}

fun <C> C.buildStreams(builder: (StreamsBuilder) -> Unit): KafkaStreams
        where C : CommonServer, C : WithKafkaStreams {
    val kafka = micro.kafka
    log.info("Configuring stream processors...")
    val kBuilder = StreamsBuilder()
    builder(kBuilder)
    val kStreams = kafka.build(kBuilder.build()).also {
        it.setUncaughtExceptionHandler { _, exception ->
            log.error("Caught fatal exception in Kafka! Stacktrace follows:")
            log.error(exception.stackTraceToString())
            stop()
        }
        log.info("Stream processors configured!")
    }
    this.kStreams = kStreams
    return kStreams
}

val CommonServer.isRunning: Boolean
    get() {
        val isKafkaRunning: Boolean? = if (this is WithKafkaStreams) {
            kStreams.state().isRunning
        } else {
            null
        }

        val serverFeature = micro.featureOrNull(ServerFeature)
        val isRpcServerRunning: Boolean? = serverFeature?.server?.isRunning

        return isKafkaRunning != false && isRpcServerRunning != false
    }

fun CommonServer.startServices(wait: Boolean = true) {
    if (this is WithKafkaStreams) {
        val kStreams = kStreams
        log.info("Starting Kafka Streams...")
        kStreams.start()
        log.info("Kafka Streams started!")
    }

    val serverFeature = micro.featureOrNull(ServerFeature)
    if (serverFeature != null) {
        log.info("Starting RPC server...")
        serverFeature.server.start()
        log.info("RPC server started!")
    }

    if (wait) {
        while (isRunning) {
            runCatching { Thread.sleep(1_000) }
        }

        // We are catching exceptions silently since we might already have called stop().
        runCatching { stop() }
    }
}

fun CommonServer.stopServices() {
    if (this is WithKafkaStreams) {
        log.info("Stopping Kafka...")
        kStreams.close(Duration.of(STOP_TIMEOUT, ChronoUnit.SECONDS))
    }

    val serverFeature = micro.featureOrNull(ServerFeature)
    if (serverFeature != null) {
        log.info("Stopping RPC server")
        serverFeature.server.stop()
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

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
 * required.
 */
interface CommonServer : BaseServer, Loggable {
    val micro: Micro

    override fun stop() {
        stopServices()
    }
}

val CommonServer.isRunning: Boolean
    get() {
        val serverFeature = micro.featureOrNull(ServerFeature)
        val isRpcServerRunning: Boolean? = serverFeature?.server?.isRunning

        return isRpcServerRunning != false
    }

fun CommonServer.startServices(wait: Boolean = true) {
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

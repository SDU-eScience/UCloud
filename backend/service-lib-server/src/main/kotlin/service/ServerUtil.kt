package dk.sdu.cloud.service

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.db.async.EnhancedPreparedStatement
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface BaseServer {
    fun start()
    fun onKtorReady() {}
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

object EmptyServer : CommonServer {
    override val micro: Micro = Micro()
    override fun start() {}
    override val log: Logger = logger()
}

val CommonServer.isRunning: Boolean
    get() {
        val serverFeature = micro.featureOrNull(ServerFeature)
        val isRpcServerRunning: Boolean? = serverFeature?.server?.isRunning

        return isRpcServerRunning != false
    }

fun CommonServer.startServices(wait: Boolean = true) = runBlocking {
    if (micro.isEmbeddedService) return@runBlocking
    EnhancedPreparedStatement.debug = micro.commandLineArguments.contains("--debug")

    @Suppress("TooGenericExceptionCaught")
    try {
        micro.eventStreamServiceOrNull?.start()
    } catch (ex: Exception) {
        log.error("Caught fatal exception in Event Stream Services")
        log.error(ex.stackTraceToString())
        stopServices()
    }

    val serverFeature = micro.featureOrNull(ServerFeature)
    if (serverFeature != null) {
        launch {
            val ktorApplicationEngine = serverFeature.ktorApplicationEngine
            if (ktorApplicationEngine != null) {
                if (micro.developmentModeEnabled) {
                    val key = MicroAttributeKey<Unit>("did-init-cors")
                    if (micro.attributes.getOrNull(key) == null) {
                        micro.attributes.set(key, Unit)
                        ktorApplicationEngine.application.install(CORS) {
                            // We run with permissive CORS settings in dev mode. This allows us to test frontend directly
                            // with local backend.
                            allowHost("frontend:9000")
                            allowHost("localhost:9000")
                            allowMethod(HttpMethod.Get)
                            allowMethod(HttpMethod.Post)
                            allowMethod(HttpMethod.Put)
                            allowMethod(HttpMethod.Delete)
                            allowMethod(HttpMethod.Head)
                            allowMethod(HttpMethod.Options)
                            allowMethod(HttpMethod.Patch)
                            allowNonSimpleContentTypes = true
                            allowCredentials = true
                            allowHeader(HttpHeaders.Authorization)
                            allowHeader("X-CSRFToken")
                            allowHeader("refreshToken")
                            allowHeader("chunked-upload-offset")
                            allowHeader("chunked-upload-token")
                            allowHeader("chunked-upload-total-size")
                            allowHeader("upload-name")
                        }
                    }
                }
            }

            serverFeature.server.start()
            log.info("RPC server started!")
        }
    }

    for (i in 1..300) {
        if (isRunning) break
        delay(100)
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
        @Suppress("TooGenericExceptionCaught")
        try {
            log.info("Stopping RPC server")
            serverFeature.server.stop()
        } catch (ex: Throwable) {
            log.warn("Caught exception while stopping RPC server!")
            log.warn(ex.stackTraceToString())
        }
    }

    @Suppress("TooGenericExceptionCaught")
    try {
        micro.featureOrNull(DeinitFeature)?.runHandlers()
    } catch (ex: Throwable) {
        log.warn("Caught exception while stopping micro features")
        log.warn(ex.stackTraceToString())
    }
}

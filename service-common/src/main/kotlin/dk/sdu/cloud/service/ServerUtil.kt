package dk.sdu.cloud.service

import dk.sdu.cloud.calls.server.FrontendOverrides
import dk.sdu.cloud.micro.DeinitFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.server
import io.ktor.application.featureOrNull
import io.ktor.application.install
import io.ktor.application.uninstall
import io.ktor.features.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

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

fun CommonServer.startServices(wait: Boolean = true) = runBlocking {
    micro.featureOrNull(FrontendOverrides)?.generate()

    log.info("Starting Event Stream Services")
    @Suppress("TooGenericExceptionCaught")
    try {
        micro.eventStreamService.start()
    } catch (ex: Exception) {
        log.error("Caught fatal exception in Event Stream Services")
        log.error(ex.stackTraceToString())
        stopServices()
    }

    val serverFeature = micro.featureOrNull(ServerFeature)
    if (serverFeature != null) {
        launch {
            log.info("Starting RPC server...")

            val ktorApplicationEngine = serverFeature.ktorApplicationEngine
            if (ktorApplicationEngine != null) {
                if (micro.developmentModeEnabled) {
                    if (ktorApplicationEngine.application.featureOrNull(CORS) == null) {
                        ktorApplicationEngine.application.install(CORS) {
                            // We run with permissive CORS settings in dev mode. This allows us to test frontend directly
                            // with local backend.
                            anyHost()
                            method(HttpMethod.Get)
                            method(HttpMethod.Post)
                            method(HttpMethod.Put)
                            method(HttpMethod.Delete)
                            method(HttpMethod.Head)
                            method(HttpMethod.Options)
                            method(HttpMethod.Patch)
                            allowNonSimpleContentTypes = true
                            allowCredentials = true
                            header(HttpHeaders.Authorization)
                            header("X-CSRFToken")
                            header("refreshToken")
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

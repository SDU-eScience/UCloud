package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.isRunning
import dk.sdu.cloud.service.db.async.PaginationV2Cache
import dk.sdu.cloud.service.startServices
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object PlaceholderServiceDescription : ServiceDescription {
    override val name = "UCloud"
    override val version = "unspecified-this-should-not-show-up-in-production"
}

class ServiceRegistry(
    val args: Array<String>,

    /**
     * [ServiceDescription] used for [rootMicro]
     *
     * Certain features should only exist once and use this information, for example, for auditing purposes. For
     * local development/testing systems this should be set to [PlaceholderServiceDescription] otherwise this should
     * be set to the correct microservice's [ServiceDescription] (done by [runAsStandalone]).
     */
    val serviceDescription: ServiceDescription
) {
    private data class ConfiguredService(
        val description: ServiceDescription,
        val service: Service,
        val scope: Micro,
        val server: CommonServer
    )

    val rootMicro = Micro()
    private val services = ArrayList<ConfiguredService>()
    val isRunning: Boolean get() = rootServer.isRunning
    private val rootServer  = object : CommonServer {
        override val micro: Micro = rootMicro
        override fun start() {
            // Do nothing
        }

        override val log: Logger = logger()
    }

    init {
        rootMicro.isEmbeddedService = false
        rootMicro.commandLineArguments = args.toList()
        rootMicro.serviceDescription = serviceDescription

        with(rootMicro) {
            install(DeinitFeature)
            install(ScriptFeature)
            install(ConfigurationFeature)
            install(ServiceDiscoveryOverrides)
            install(ServiceInstanceFeature)
            install(DevelopmentOverrides)
            install(LogFeature)
            install(KtorServerProviderFeature)
            install(ClientFeature)
            install(RedisFeature)
            install(TokenValidationFeature)
            install(ServerFeature)
            install(HealthCheckFeature)
            install(BackgroundScopeFeature)
            //install(DatabaseConfigurationFeature)
            //install(FlywayFeature)
        }

        // TODO Move it somewhere else
        PaginationV2Cache.init(rootMicro.backgroundScope)
    }

    fun register(service: Service) {
        val scopedMicro = rootMicro.createScope()
        scopedMicro.serviceDescription = service.description
        scopedMicro.isEmbeddedService = true
        scopedMicro.install(DatabaseConfigurationFeature)
        scopedMicro.install(FlywayFeature)
        scopedMicro.install(RedisFeature)
        val server = service.initializeServer(scopedMicro)
        services.add(ConfiguredService(service.description, service, scopedMicro, server))
    }

    fun start(wait: Boolean = true) {
        var scriptHandlerExit = false
        try {
            for (service in services) {
                if (service.scope.runScriptHandler()) {
                    scriptHandlerExit = true
                    continue
                }
            }
        } catch (ex: Throwable) {
            println("CAUGHT FATAL ERROR DURING SCRIPT HANDLER EXECUTION")
            println(ex.stackTraceToString())
            exitProcess(1)
        }

        if (scriptHandlerExit) {
            stop(stopServices = false)
            return
        }

        try {
            for (service in services) {
                service.server.start()
            }
        } catch (ex: Throwable) {
            println("CAUGHT FATAL ERROR DURING SERVER START")
            println(ex.stackTraceToString())
            exitProcess(1)
        }

        try {
            rootServer.startServices(wait = false)
        } catch (ex: Throwable) {
            println("CAUGHT FATAL ERROR WHILE STARTING SERVICES")
            println(ex.stackTraceToString())
            exitProcess(1)
        }

        try {
            for (service in services) {
                service.server.onKtorReady()
            }
        } catch (ex: Throwable) {
            println("CAUGHT FATAL ERROR WHILE REPORTING KTOR READY")
            println(ex.stackTraceToString())
            exitProcess(1)
        }

        // Note this code runs before logger is ready
        ucloudIsReady.set(true)
        println("============ UCloud is ready ============")

        if (wait) {
            while (rootServer.isRunning) {
                Thread.sleep(50)
            }
        }
    }

    fun stop(stopServices: Boolean = true) {
        if (stopServices) {
            for (service in services) {
                service.server.stop()
            }
        }
        rootServer.stop()
    }
}

val ucloudIsReady = AtomicBoolean(false)

interface Service {
    val description: ServiceDescription
    fun initializeServer(micro: Micro): CommonServer
}

fun Service.runAsStandalone(args: Array<String>) {
    ServiceRegistry(args, description).apply {
        register(this@runAsStandalone)
        start()
    }
}

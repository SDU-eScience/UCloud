package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.FrontendOverrides
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.isRunning
import dk.sdu.cloud.service.startServices
import org.slf4j.Logger

var Micro.isEmbeddedService: Boolean by delegate("isRootContainer")

class ServiceRegistry(val args: Array<String>) {
    private data class ConfiguredService(val description: ServiceDescription, val service: Service, val scope: Micro, val server: CommonServer)

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
        rootMicro.serviceDescription = object : ServiceDescription {
            override val name = "UCloud"
            override val version = "unspecified"
        }

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
            install(FrontendOverrides)
            install(ServerFeature)
            install(HealthCheckFeature)
            //install(DatabaseConfigurationFeature)
            //install(FlywayFeature)
        }
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

    fun start() {
        var scriptHandlerExit = false
        for (service in services) {
            if (service.scope.runScriptHandler()) {
                scriptHandlerExit = true
                continue
            }
        }

        if (scriptHandlerExit) {
            stop(stopServices = false)
            return
        }

        for (service in services) {
            service.server.start()
        }

        rootServer.startServices(wait = false)

        for (service in services) {
            service.server.onKtorReady()
        }

        while (rootServer.isRunning) {
            Thread.sleep(50)
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

interface Service {
    val description: ServiceDescription
    fun initializeServer(micro: Micro): CommonServer
}

fun Service.runAsStandalone(args: Array<String>) {
    ServiceRegistry(args).apply {
        register(this@runAsStandalone)
        start()
    }
}

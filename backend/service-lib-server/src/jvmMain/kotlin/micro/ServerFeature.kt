package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.server.engine.ApplicationEngine

class ServerFeature : MicroFeature {
    private lateinit var ctx: Micro
    val server = RpcServer()
    var ktorApplicationEngine: ApplicationEngine? = null
        private set

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        this.ctx = ctx
        log.debug("Installing server...")

        val eventStreamService = ctx.eventStreamServiceOrNull
        ClientInfoInterceptor().register(server)
        JobIdInterceptor(!ctx.developmentModeEnabled).register(server)
        AuthInterceptor(ctx.developmentModeEnabled).register(server)
        ProjectInterceptor().register(server)
        if (eventStreamService != null) {
            AuditToEventStream(ctx.serviceInstance, eventStreamService, ctx.tokenValidation).register(server)
        }

        val serverConfig = ctx.rpcConfiguration?.server
        val installHttp = serverConfig?.http != false

        if (installHttp) {
            log.trace("Installing HTTP server")
            val engine = ctx.serverProvider {
                installDefaultFeatures()
            }

            engine.start(wait = false)
            ktorApplicationEngine = engine
            server.attachRequestInterceptor(IngoingHttpInterceptor(engine, server))
            server.attachRequestInterceptor(IngoingWebSocketInterceptor(engine, server))
        }

        ctx.featureOrNull(DeinitFeature)?.addHandler {
            server.stop()
        }
    }

    companion object Feature : MicroFeatureFactory<ServerFeature, Unit>, Loggable {
        override val log = logger()
        override val key: MicroAttributeKey<ServerFeature> = MicroAttributeKey("rpc-server-feature")
        override fun create(config: Unit): ServerFeature = ServerFeature()
    }
}

val Micro.server: RpcServer
    get() = feature(ServerFeature).server

package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.AuditToKafkaStream
import dk.sdu.cloud.calls.server.AuthInterceptor
import dk.sdu.cloud.calls.server.ClientInfoInterceptor
import dk.sdu.cloud.calls.server.IngoingHttpInterceptor
import dk.sdu.cloud.calls.server.IngoingWebSocketInterceptor
import dk.sdu.cloud.calls.server.JobIdInterceptor
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.server.engine.ApplicationEngine

class ServerFeature : MicroFeature {
    private lateinit var ctx: Micro
    val server = RpcServer()
    var ktorApplicationEngine: ApplicationEngine? = null
        private set

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        this.ctx = ctx
        log.info("Installing server...")

        ClientInfoInterceptor().register(server)
        JobIdInterceptor(!ctx.developmentModeEnabled).register(server)
        AuditToKafkaStream(ctx.serviceInstance, ctx.kafka, ctx.tokenValidation).register(server)
        AuthInterceptor(ctx.tokenValidation).register(server)

        val serverConfig = ctx.rpcConfiguration?.server
        val installHttp = serverConfig?.http != false

        if (installHttp) {
            log.info("Installing HTTP server")
            val engine = ctx.serverProvider {
                installDefaultFeatures()
            }

            engine.start(wait = false)
            ktorApplicationEngine = engine
            server.attachRequestInterceptor(IngoingHttpInterceptor(engine, server))
            server.attachRequestInterceptor(IngoingWebSocketInterceptor(engine, server))
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

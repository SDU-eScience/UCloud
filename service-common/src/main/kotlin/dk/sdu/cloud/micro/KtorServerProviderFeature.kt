package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.Loggable
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

private const val DEFAULT_PORT = 8080

class KtorServerProviderFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.serverProvider = { module ->
            embeddedServer(
                Netty,
                port = ctx.featureOrNull(ServiceDiscoveryOverrides)?.get(serviceDescription.name)?.port
                    ?: DEFAULT_PORT,
                module = module,
                configure = {
                    responseWriteTimeoutSeconds = 0
                }
            )
        }
    }

    companion object Feature : MicroFeatureFactory<KtorServerProviderFeature, Unit>,
        Loggable {
        override val key: MicroAttributeKey<KtorServerProviderFeature> =
            MicroAttributeKey("ktor-server-provider-feature")

        override fun create(config: Unit): KtorServerProviderFeature =
            KtorServerProviderFeature()

        override val log = logger()

        private val officialEngines = listOf(
            "io.ktor.server.cio.CIO",
            "io.ktor.server.jetty.Jetty",
            "io.ktor.server.tomcat.Tomcat",
            "io.ktor.server.netty.Netty"
        )

        internal val serverProviderKey =
            MicroAttributeKey<HttpServerProvider>("ktor-server-provider-key")
    }
}

var Micro.serverProvider: HttpServerProvider
    get() = attributes[KtorServerProviderFeature.serverProviderKey]
    private set(value) {
        attributes[KtorServerProviderFeature.serverProviderKey] = value
    }

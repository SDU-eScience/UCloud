package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.Loggable
import io.ktor.server.cio.*
import io.ktor.server.engine.embeddedServer

private const val DEFAULT_PORT = 8080

class KtorServerProviderFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val port = ctx.configuration.requestChunkAtOrNull("servicePort") ?: DEFAULT_PORT
        ctx.serverProvider = { module ->
            embeddedServer(
                CIO,
                port = port,
                module = module,
                configure = {
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

        val serverProviderKey =
            MicroAttributeKey<HttpServerProvider>("ktor-server-provider-key")
    }
}

var Micro.serverProvider: HttpServerProvider
    get() = attributes[KtorServerProviderFeature.serverProviderKey]
    private set(value) {
        attributes[KtorServerProviderFeature.serverProviderKey] = value
    }

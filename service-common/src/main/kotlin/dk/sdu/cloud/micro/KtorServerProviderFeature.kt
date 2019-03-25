package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.Loggable
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.embeddedServer

private const val DEFAULT_PORT = 8080

class KtorServerProviderFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        for (engine in officialEngines) {
            log.debug("Attempting to load engine: $engine...")
            val loadedClass = try {
                javaClass.classLoader.loadClass(engine)
            } catch (_: Exception) {
                log.debug("... Could not load engine")
                continue
            }

            log.debug("... Engine loaded")

            val engineFactory = loadedClass.kotlin.objectInstance as ApplicationEngineFactory<*, *>

            ctx.serverProvider = { module ->
                embeddedServer(
                    engineFactory,
                    port = ctx.featureOrNull(ServiceDiscoveryOverrides)?.get(serviceDescription.name)?.port
                        ?: DEFAULT_PORT,
                    module = module
                )
            }

            log.info("Using application engine: $engineFactory loaded from $engine")
            break
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

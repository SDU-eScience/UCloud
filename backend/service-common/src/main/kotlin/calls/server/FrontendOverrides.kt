package dk.sdu.cloud.calls.server

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.HttpPathSegment
import dk.sdu.cloud.calls.httpOrNull
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.MicroAttributeKey
import dk.sdu.cloud.micro.MicroFeature
import dk.sdu.cloud.micro.MicroFeatureFactory
import dk.sdu.cloud.micro.RpcConfiguration
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.server
import io.ktor.http.HttpMethod
import java.io.File

class FrontendOverrides : MicroFeature {
    private data class Override(
        val path: String,
        val method: HttpMethod,
        val destination: Destination
    )

    private data class Destination(val scheme: String = "http", val host: String = "localhost", val port: Int)

    private lateinit var ctx: Micro

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        this.ctx = ctx
    }

    private fun generate(
        localPort: Int,
        handlers: List<RpcServer.Companion.DelayedHandler<*, *, *>>
    ): List<Override> {
        return handlers.mapNotNull { (call, _, _) ->
            val http = call.httpOrNull ?: return@mapNotNull null
            val segments = ArrayList<String>()
            segments.addAll(http.path.basePath.split("/").filter { it.isNotEmpty() })

            loop@ for (segment in http.path.segments) {
                when (segment) {
                    is HttpPathSegment.Simple -> segments.add(segment.text)
                    is HttpPathSegment.Property<*, *> -> break@loop
                    is HttpPathSegment.Remaining -> break@loop
                }
            }

            val path = segments.joinToString("/")

            Override(path, http.method, Destination(port = localPort))
        }
    }

    fun generate() {
        val config = ctx.frontendOverridesConfiguration ?: return
        val outputFile = File(config.configDir, ctx.serviceDescription.name)
        val port =
            ctx.featureOrNull(ServiceDiscoveryOverrides)?.get(ctx.serviceDescription.name)?.port ?: 8080

        defaultMapper.writeValue(outputFile, generate(port, ctx.server.delayedHandlers))
    }

    companion object : MicroFeatureFactory<FrontendOverrides, Unit> {
        override val key: MicroAttributeKey<FrontendOverrides> = MicroAttributeKey("frontend-overrides")

        override fun create(config: Unit): FrontendOverrides {
            return FrontendOverrides()
        }
    }
}

data class FrontendOverridesConfiguration(val configDir: String)

internal val Micro.frontendOverridesConfiguration: FrontendOverridesConfiguration?
    get() = configuration.requestChunkAtOrNull("development", "frontend")

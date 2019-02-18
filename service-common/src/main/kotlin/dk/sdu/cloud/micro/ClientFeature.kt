package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.client.FixedOutgoingHostResolver
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHostResolver
import dk.sdu.cloud.calls.client.OutgoingHttpRequestInterceptor
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.service.DevelopmentOutgoingHostResolver
import dk.sdu.cloud.service.Loggable

class ClientFeature : MicroFeature {
    private lateinit var ctx: Micro
    val client: RpcClient = RpcClient()
    lateinit var hostResolver: OutgoingHostResolver
        private set

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        this.ctx = ctx

        val clientConfig = ctx.rpcConfiguration?.client
        val defaultHost = clientConfig?.host ?: HostInfo(scheme = "https", host = "cloud.sdu.dk", port = 443)
        val installHttp = clientConfig?.http != false

        val defaultOutgoingHostResolver = FixedOutgoingHostResolver(defaultHost)

        val hostResolver = if (ctx.developmentModeEnabled) {
            log.info("Initializing client with development mode overrides!")
            val overrides = ctx.feature(ServiceDiscoveryOverrides)

            DevelopmentOutgoingHostResolver(defaultOutgoingHostResolver, overrides)
        } else {
            defaultOutgoingHostResolver
        }

        this.hostResolver = hostResolver

        if (installHttp) {
            OutgoingHttpRequestInterceptor()
                .install(
                    client,
                    hostResolver
                )
        }
    }

    companion object Feature : MicroFeatureFactory<ClientFeature, Unit>, Loggable {
        override val log = logger()
        override val key: MicroAttributeKey<ClientFeature> = MicroAttributeKey("cloud-feature")
        override fun create(config: Unit): ClientFeature = ClientFeature()
    }
}

val Micro.client: RpcClient
    get() = feature(ClientFeature).client

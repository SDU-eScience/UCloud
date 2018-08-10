package dk.sdu.cloud.bare

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.bare.api.BareServiceDescription
import dk.sdu.cloud.bare.api.PingStreamDescriptions
import dk.sdu.cloud.bare.http.PingController
import dk.sdu.cloud.bare.processor.PingProcessor
import dk.sdu.cloud.service.*
import io.ktor.application.install
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val config: Configuration,
    private val ktor: HttpServerProvider
) : CommonServer, Loggable {
    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams
    override val log = logger()

    override fun start() {
        val instance = BareServiceDescription.instance(config.connConfig)

        kStreams = buildStreams { kBuilder ->
            PingProcessor(kBuilder.stream(PingStreamDescriptions.stream)).also { it.configure() }
        }

        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, instance)
            install(JWTProtection)

            routing {
                configureControllers(
                    PingController()
                )
            }
        }

        startServices()
    }
}
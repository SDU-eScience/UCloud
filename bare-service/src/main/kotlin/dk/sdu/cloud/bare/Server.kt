package dk.sdu.cloud.bare

import dk.sdu.cloud.bare.api.BareServiceDescription
import dk.sdu.cloud.bare.api.PingStreamDescriptions
import dk.sdu.cloud.bare.http.PingController
import dk.sdu.cloud.bare.processor.PingProcessor
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.*
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val cloud: AuthenticatedCloud,
    private val ktor: HttpServerProvider
) : CommonServer, Loggable {
    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams
    override val log = logger()

    override fun start() {
        kStreams = buildStreams { kBuilder ->
            PingProcessor(kBuilder.stream(PingStreamDescriptions.stream)).also { it.configure() }
        }

        httpServer = ktor {
            installDefaultFeatures(
                cloud,
                kafka,
                // TODO This one
                ServiceInstance(BareServiceDescription.definition(), "localhost", 8080),
                requireJobId = false
            )
//            install(JWTProtection)

            routing {
                configureControllers(
                    PingController()
                )
            }
        }

        startServices()
        log.info("Ready!")
    }
}
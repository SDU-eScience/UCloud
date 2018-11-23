package dk.sdu.cloud.filesearch

import dk.sdu.cloud.filesearch.http.SearchController
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger

/**
 * A server for the filesearch-service
 */
class Server(
    override val kafka: KafkaServices,
    private val http: HttpServerProvider,
    private val micro: Micro
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null
    override val log: Logger = logger()

    override fun start() {
        httpServer = http {
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    SearchController()
                )
            }
        }

        startServices()
    }
}

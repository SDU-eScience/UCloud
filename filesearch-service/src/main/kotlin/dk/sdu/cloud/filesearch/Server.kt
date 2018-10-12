package dk.sdu.cloud.filesearch

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.filesearch.http.SearchController
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger

class Server(
    override val kafka: KafkaServices,
    private val http: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val serviceInstance: ServiceInstance
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null
    override val log: Logger = logger()

    override fun start() {
        httpServer = http {
            installDefaultFeatures(cloud, kafka, serviceInstance)

            routing {
                configureControllers(
                    SearchController()
                )
            }
        }

        startServices()
    }
}

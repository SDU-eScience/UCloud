package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.accounting.compute.http.AccountingController
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val serviceInstance: ServiceInstance
): CommonServer {
    override val log = logger()
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null

    override fun start() {
        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, serviceInstance)

            routing {
                configureControllers(
                    AccountingController()
                )
            }
        }

        log.info("Server is ready!")
    }

    override fun stop() {
        super.stop()
    }
}
package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.accounting.compute.http.ComputeAccountingController
import dk.sdu.cloud.accounting.compute.http.ComputeTimeController
import dk.sdu.cloud.accounting.compute.http.JobsStartedController
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.startServices
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val serviceInstance: ServiceInstance
) : CommonServer {
    override val log = logger()
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null

    override fun start() {
        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, serviceInstance)

            routing {
                configureControllers(
                    JobsStartedController(),
                    ComputeTimeController(),
                    ComputeAccountingController()
                )
            }
        }

        log.info("Server is ready!")

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}

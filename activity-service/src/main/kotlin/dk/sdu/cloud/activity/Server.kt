package dk.sdu.cloud.activity

import dk.sdu.cloud.activity.http.ActivityController
import dk.sdu.cloud.activity.processor.StorageAuditProcessor
import dk.sdu.cloud.activity.services.ActivityEventDao
import dk.sdu.cloud.activity.services.InMemoryActivityEventDao
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.FakeDBSessionFactory
import dk.sdu.cloud.service.db.HibernateSessionFactory
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val instance: ServiceInstance
) : CommonServer {
    override val log = logger()

    override val kStreams: KafkaStreams? = null
    override lateinit var httpServer: ApplicationEngine

    private val allProcessors = ArrayList<EventConsumer<*>>()

    override fun start() {
        log.info("Creating core services")
        val db = FakeDBSessionFactory
        val activityEventDao = InMemoryActivityEventDao()
        log.info("Core services constructed")

        log.info("Creating stream processors")
        allProcessors.addAll(StorageAuditProcessor(kafka, db, activityEventDao).init())
        log.info("Stream processors constructed")

        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, instance)

            routing {
                configureControllers(
                    ActivityController(db, activityEventDao)
                )
            }
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        allProcessors.forEach { it.close() }
    }
}
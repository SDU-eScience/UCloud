package dk.sdu.cloud.project

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.project.api.ProjectServiceDescription
import dk.sdu.cloud.project.http.ProjectsController
import dk.sdu.cloud.project.processors.RequestProcessor
import dk.sdu.cloud.project.services.ProjectsDAO
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.instance
import dk.sdu.cloud.service.markServiceAsReady
import dk.sdu.cloud.service.registerService
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.zookeeper.ZooKeeper
import org.slf4j.LoggerFactory
import stackTraceToString
import java.util.concurrent.TimeUnit

class Server(
        private val configuration: Configuration,
        private val kafka: KafkaServices,
        private val zk: ZooKeeper,
        private val ktor: HttpServerProvider,
        private val cloud: RefreshingJWTAuthenticator
) {
    private lateinit var httpServer: ApplicationEngine
    private lateinit var kStreams: KafkaStreams

    fun start() {
        val instance = ProjectServiceDescription.instance(configuration.connConfig)
        val node = runBlocking {
            log.info("Registering service...")
            zk.registerService(instance).also {
                log.debug("Service registered! Got back node: $it")
            }
        }

        log.info("Creating core services")
        val projectsDao = ProjectsDAO()
        log.info("Core services constructed!")

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")
            RequestProcessor().configure(kBuilder)
            log.info("Stream processors configured!")

            kafka.build(kBuilder.build()).also {
                log.info("Kafka Streams Topology successfully built!")
            }
        }

        kStreams.setUncaughtExceptionHandler { _, exception ->
            log.error("Caught fatal exception in Kafka! Stacktrace follows:")
            log.error(exception.stackTraceToString())
            stop()
        }

        httpServer = ktor {
            log.info("Configuring HTTP server")

            // Installs some basic default features required by all services. This includes:
            //  - Content negation (for JSON, done using Jackson with some additional config)
            //  - Populates the call.request.jobId field (this is created by the GW)
            //  - Adds default headers and call logging
            installDefaultFeatures()
            install(JWTProtection)

            routing {
                route("api/projects") {
                    // Route.protect() allows us to protect a single route. This should only be called once per
                    // route. This will check the JWT and fail if it is not present. It is also possible to provide
                    // a list of roles that are suitable for the route.

                    // TODO This should probably be on by default on all routes.
                    // Look into this when making a feature out of this?
                    protect()

                    ProjectsController(projectsDao).also { it.configure(this) }
                }
            }

            log.info("HTTP server successfully configured!")
        }

        log.info("Starting HTTP server...")
        httpServer.start(wait = false)
        log.info("HTTP server started!")

        log.info("Starting Kafka Streams...")
        kStreams.start()
        log.info("Kafka Streams started!")

        runBlocking { zk.markServiceAsReady(node, instance) }
        log.info("Server is ready!")
    }

    fun stop() {
        httpServer.stop(gracePeriod = 0, timeout = 30, timeUnit = TimeUnit.SECONDS)
        kStreams.close(30, TimeUnit.SECONDS)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Server::class.java)
    }
}

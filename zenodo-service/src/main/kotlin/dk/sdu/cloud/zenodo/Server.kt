package dk.sdu.cloud.zenodo

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.zenodo.api.ZenodoCommandStreams
import dk.sdu.cloud.zenodo.api.ZenodoServiceDescription
import dk.sdu.cloud.zenodo.http.ZenodoController
import dk.sdu.cloud.zenodo.processors.PublishProcessor
import dk.sdu.cloud.zenodo.services.*
import dk.sdu.cloud.zenodo.services.hibernate.PublicationHibernateDAO
import dk.sdu.cloud.zenodo.services.hibernate.ZenodoOAuthHibernateStateStore
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class Server(
    private val db: HibernateSessionFactory,
    private val cloud: AuthenticatedCloud,
    private val kafka: KafkaServices,
    private val serviceRegistry: ServiceRegistry,
    private val config: Configuration,
    private val ktor: HttpServerProvider
) {
    private var initialized = false

    private lateinit var httpServer: ApplicationEngine
    private lateinit var kStreams: KafkaStreams

    fun start() {
        if (initialized) throw IllegalStateException("Already started!")

        val instance = ZenodoServiceDescription.instance(config.connConfig)

        val zenodoOauth = ZenodoOAuth(
            db = db,
            clientSecret = config.zenodo.clientSecret,
            clientId = config.zenodo.clientId,

            // TODO FIX THIS
            callback = if (config.production) "https://cloud.sdu.dk/zenodo/oauth" else "http://localhost:42250/zenodo/oauth",

            stateStore = ZenodoOAuthHibernateStateStore(),

            useSandbox = true // TODO FIX THIS
        )

        val zenodo = ZenodoRPCService(zenodoOauth)

        val publicationService = PublicationHibernateDAO()

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")
            PublishProcessor(db, zenodo, publicationService, cloud.parent).also { it.init(kBuilder) }
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
            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)
            install(JWTProtection)

            routing {
                route("zenodo") {
                    get("oauth") {
                        val state =
                            call.request.queryParameters["state"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val code =
                            call.request.queryParameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest)

                        val redirectTo = zenodoOauth.requestTokenWithCode(code, state) ?: "/"
                        call.respondRedirect(redirectTo)
                    }
                }

                route("/api/zenodo") {
                    ZenodoController(
                        db,
                        publicationService,
                        zenodo,
                        kafka.producer.forStream(ZenodoCommandStreams.publishCommands)
                    ).also { it.configure(this) }
                }
            }
        }

        log.info("Starting HTTP server...")
        httpServer.start(wait = false)
        log.info("HTTP server started!")

        log.info("Starting Kafka Streams...")
        kStreams.start()
        log.info("Kafka Streams started!")

        initialized = true
        serviceRegistry.register(listOf("/api/zenodo", "/zenodo/oauth"))
        log.info("Server is ready!")
        log.info(instance.toString())
    }

    fun stop() {
        httpServer.stop(gracePeriod = 0, timeout = 30, timeUnit = TimeUnit.SECONDS)
        kStreams.close(30, TimeUnit.SECONDS)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Server::class.java)
    }
}
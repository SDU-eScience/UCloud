package dk.sdu.cloud.zenodo

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.zenodo.api.ZenodoAccessRedirectURL
import dk.sdu.cloud.zenodo.api.ZenodoConnectedStatus
import dk.sdu.cloud.zenodo.api.ZenodoDescriptions
import dk.sdu.cloud.zenodo.api.ZenodoServiceDescription
import dk.sdu.cloud.zenodo.processors.PublishProcessor
import dk.sdu.cloud.zenodo.services.InMemoryZenodoOAuthStateStore
import dk.sdu.cloud.zenodo.services.ZenodoOAuth
import dk.sdu.cloud.zenodo.services.ZenodoService
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.zookeeper.ZooKeeper
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

class Server(
    private val cloud: AuthenticatedCloud,
    private val kafka: KafkaServices,
    private val zk: ZooKeeper,
    private val config: Configuration,
    private val ktor: HttpServerProvider
) {
    private var initialized = false

    private lateinit var httpServer: ApplicationEngine
    private lateinit var kStreams: KafkaStreams

    fun start() {
        if (initialized) throw IllegalStateException("Already started!")

        val instance = ZenodoServiceDescription.instance(config.connConfig)
        val node = runBlocking {
            log.info("Registering service...")
            zk.registerService(instance).also {
                log.debug("Service registered! Got back node: $it")
            }
        }

        val zenodoOauth = ZenodoOAuth(
            config.zenodo.clientSecret,
            config.zenodo.clientId,
            "http://localhost:42250/zenodo/oauth", // TODO FIX THIS
            InMemoryZenodoOAuthStateStore.load(), // TODO FIX THIS
            true // TODO FIX THIS
        )

        val zenodo = ZenodoService(zenodoOauth)

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")
            PublishProcessor(zenodo, cloud.parent).also { it.init(kBuilder) }
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

                route("api") {
                    route("zenodo") {
                        protect()

                        implement(ZenodoDescriptions.requestAccess) {
                            logEntry(log, it)
                            val returnToURL = URL(it.returnTo)
                            if (returnToURL.protocol !in setOf("http", "https")) {
                                error(CommonErrorMessage("Bad Request"), HttpStatusCode.BadRequest)
                                return@implement
                            }

                            if (returnToURL.host !in setOf("localhost", "cloud.sdu.dk")) {
                                // TODO This should be handled in a more generic way
                                error(CommonErrorMessage("Bad Request"), HttpStatusCode.BadRequest)
                                return@implement
                            }

                            val who = call.request.validatedPrincipal
                            ok(
                                ZenodoAccessRedirectURL(
                                    zenodo.createAuthorizationUrl(who, it.returnTo).toExternalForm()
                                )
                            )
                        }

                        implement(ZenodoDescriptions.status) {
                            logEntry(log, it)

                            ok(ZenodoConnectedStatus(zenodo.isConnected(call.request.validatedPrincipal)))
                        }
                    }
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
        runBlocking { zk.markServiceAsReady(node, instance) }
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
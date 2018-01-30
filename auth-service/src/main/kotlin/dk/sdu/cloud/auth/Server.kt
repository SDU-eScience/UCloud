package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.onelogin.saml2.settings.Saml2Settings
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.http.CoreAuthController
import dk.sdu.cloud.auth.http.PasswordController
import dk.sdu.cloud.auth.http.SAMLController
import dk.sdu.cloud.auth.processors.OneTimeTokenProcessor
import dk.sdu.cloud.auth.processors.RefreshTokenProcessor
import dk.sdu.cloud.auth.processors.UserProcessor
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.*
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.zookeeper.ZooKeeper
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class AuthServer(
    private val cloud: AuthenticatedCloud,
    private val jwtAlg: Algorithm,
    private val config: AuthConfiguration,
    private val authSettings: Saml2Settings,
    private val zk: ZooKeeper,
    private val kafka: KafkaServices,
    private val ktor: HttpServerProvider
) {
    private lateinit var kStreams: KafkaStreams
    private lateinit var httpServer: ApplicationEngine

    fun start() {
        val instance = AuthServiceDescription.instance(config.connConfig)
        val node = runBlocking {
            log.info("Registering service...")
            zk.registerService(instance).also {
                log.debug("Service registered! Got back node: $it")
            }
        }

        log.info("Creating core services...")
        val tokenService = TokenService(
            jwtAlg,
            kafka.producer.forStream(AuthStreams.UserUpdateStream),
            kafka.producer.forStream(AuthStreams.RefreshTokenStream),
            kafka.producer.forStream(AuthStreams.OneTimeTokenStream)
        )
        log.info("Core services constructed!")

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")

            RefreshTokenProcessor(kBuilder.stream(AuthStreams.RefreshTokenStream)).also { it.init() }
            UserProcessor(kBuilder.stream(AuthStreams.UserUpdateStream)).also { it.init() }
            OneTimeTokenProcessor(kBuilder.stream(AuthStreams.OneTimeTokenStream)).also { it.init() }

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

            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)

            log.info("Creating HTTP controllers")
            val coreController = CoreAuthController(tokenService, config.enablePasswords, config.enableWayf)
            val samlController = SAMLController(authSettings, tokenService)
            val passwordController = PasswordController(tokenService)
            log.info("HTTP controllers configured!")

            routing {
                coreController.configure(this)
                if (config.enableWayf) samlController.configure(this)
                if (config.enablePasswords) passwordController.configure(this)
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
        log.info(instance.toString())
    }

    fun stop() {
        try {
            kStreams.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            httpServer.stop(0, 5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            kafka.producer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthServer::class.java)
    }
}

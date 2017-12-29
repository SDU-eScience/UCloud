package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.zafarkhaja.semver.Version
import com.onelogin.saml2.settings.SettingsBuilder
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.http.CoreAuthController
import dk.sdu.cloud.auth.http.SAMLController
import dk.sdu.cloud.auth.processors.RefreshTokenProcessor
import dk.sdu.cloud.auth.processors.UserProcessor
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.saml.validateOrThrow
import dk.sdu.cloud.service.*
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.security.interfaces.RSAPrivateKey
import java.util.*
import java.util.concurrent.TimeUnit

data class KafkaConfiguration(val servers: List<String>)
data class DatabaseConfiguration(
        val url: String,
        val driver: String,
        val username: String,
        val password: String
)

data class AuthConfiguration(
        val kafka: KafkaConfiguration,
        val zookeeper: ZooKeeperHostInfo,
        val database: DatabaseConfiguration
)

class AuthServer(
        samlSettings: Properties,
        privKey: RSAPrivateKey,
        private val kafkaStreamsConfiguration: Properties,
        private val kafkaProducerConfiguration: Properties,
        private val config: AuthConfiguration,
        private val hostname: String,
        private val port: Int = 42300
) {
    private val log = LoggerFactory.getLogger(AuthServer::class.java)
    private val jwtAlg = Algorithm.RSA256(privKey)
    private val authSettings = SettingsBuilder().fromProperties(samlSettings).build().validateOrThrow()
    private lateinit var eventProducer: KafkaProducer<String, String>
    private lateinit var streams: KafkaStreams
    private lateinit var server: ApplicationEngine


    fun start(wait: Boolean = true) {
        val serviceDefinition = ServiceDefinition(
                AuthServiceDescription.name,
                Version.valueOf(AuthServiceDescription.version)
        )

        val instance = ServiceInstance(serviceDefinition, hostname, port)
        val (zk, node) = runBlocking {
            val zk = ZooKeeperConnection(listOf(config.zookeeper)).connect()
            val node = zk.registerService(instance)
            Pair(zk, node)
        }

        Database.connect(
                url = config.database.url,
                driver = config.database.driver,

                user = config.database.username,
                password = config.database.password
        )

        val streamsBuilder = StreamsBuilder()
        eventProducer = KafkaProducer(kafkaProducerConfiguration)

        val tokenService = TokenService(
                jwtAlg,
                eventProducer.forStream(AuthStreams.UserUpdateStream),
                eventProducer.forStream(AuthStreams.RefreshTokenStream)
        )
        val coreController = CoreAuthController(tokenService)
        val samlController = SAMLController(authSettings, tokenService)

        val refreshTokenProcessor = RefreshTokenProcessor(streamsBuilder.stream(AuthStreams.RefreshTokenStream))
        val userProcessor = UserProcessor(streamsBuilder.stream(AuthStreams.UserUpdateStream))

        refreshTokenProcessor.init()
        userProcessor.init()

        server = embeddedServer(Netty, port = port) {
            install(DefaultHeaders)
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }

            routing {
                coreController.configure(this)
                samlController.configure(this)
            }
        }

        streams = KafkaStreams(streamsBuilder.build(), kafkaStreamsConfiguration)
        streams.setUncaughtExceptionHandler { _, b ->
            log.warn("Caught critical exception in Kafka!")
            log.warn(StringWriter().apply { b.printStackTrace(PrintWriter(this)) }.toString())
            stop()
        }

        streams.start()
        server.start(wait = false)

        runBlocking { zk.markServiceAsReady(node, instance) }
    }

    fun stop() {
        try {
            streams.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            server.stop(0, 5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            eventProducer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


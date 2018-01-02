package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.onelogin.saml2.settings.SettingsBuilder
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.http.CoreAuthController
import dk.sdu.cloud.auth.http.PasswordController
import dk.sdu.cloud.auth.http.SAMLController
import dk.sdu.cloud.auth.processors.RefreshTokenProcessor
import dk.sdu.cloud.auth.processors.UserProcessor
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.saml.validateOrThrow
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.KafkaUtil.retrieveKafkaProducerConfiguration
import dk.sdu.cloud.service.KafkaUtil.retrieveKafkaStreamsConfiguration
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

data class DatabaseConfiguration(
        val url: String,
        val driver: String,
        val username: String,
        val password: String
)

data class AuthConfiguration(
        val enablePasswords: Boolean = true,
        val enableWayf: Boolean = false,
        val database: DatabaseConfiguration,
        val connection: RawConnectionConfig
)

class AuthServer(
        samlSettings: Properties,
        privKey: RSAPrivateKey,
        private val config: AuthConfiguration
) {
    private val log = LoggerFactory.getLogger(AuthServer::class.java)
    private val jwtAlg = Algorithm.RSA256(privKey)
    private val authSettings = SettingsBuilder().fromProperties(samlSettings).build().validateOrThrow()
    private lateinit var eventProducer: KafkaProducer<String, String>
    private lateinit var streams: KafkaStreams
    private lateinit var server: ApplicationEngine

    fun start() {
        val connConfig = config.connection.processed

        // Register service
        val instance = AuthServiceDescription.instance(connConfig)
        val (zk, node) = runBlocking {
            val zk = ZooKeeperConnection(connConfig.zookeeper.servers).connect()
            val node = zk.registerService(instance)
            Pair(zk, node)
        }

        // Services
        val streamsBuilder = StreamsBuilder()
        eventProducer = KafkaProducer(retrieveKafkaProducerConfiguration(connConfig))

        val tokenService = TokenService(
                jwtAlg,
                eventProducer.forStream(AuthStreams.UserUpdateStream),
                eventProducer.forStream(AuthStreams.RefreshTokenStream)
        )

        Database.connect(
                url = config.database.url,
                driver = config.database.driver,

                user = config.database.username,
                password = config.database.password
        )

        // HTTP Controllers
        val coreController = CoreAuthController(tokenService, config.enablePasswords, config.enableWayf)
        val samlController = SAMLController(authSettings, tokenService)
        val passwordController = PasswordController(tokenService)

        // Kafka Processors
        val refreshTokenProcessor = RefreshTokenProcessor(streamsBuilder.stream(AuthStreams.RefreshTokenStream))
        val userProcessor = UserProcessor(streamsBuilder.stream(AuthStreams.UserUpdateStream))

        // Processor Initialization
        refreshTokenProcessor.init()
        userProcessor.init()

        // HTTP Server Initialization
        server = embeddedServer(Netty, port = connConfig.service.port) {
            install(DefaultHeaders)
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }

            routing {
                coreController.configure(this)
                if (config.enableWayf) samlController.configure(this)
                if (config.enablePasswords) passwordController.configure(this)
            }
        }

        // Streams Initialization
        streams = KafkaStreams(streamsBuilder.build(), retrieveKafkaStreamsConfiguration(connConfig))
        streams.setUncaughtExceptionHandler { _, b ->
            log.warn("Caught critical exception in Kafka!")
            log.warn(StringWriter().apply { b.printStackTrace(PrintWriter(this)) }.toString())
            stop()
        }

        // Start HTTP Server and Kafka Streams
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


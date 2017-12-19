package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.zafarkhaja.semver.Version
import com.onelogin.saml2.settings.SettingsBuilder
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.zookeeper.ZooDefs
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.http.CoreAuthController
import dk.sdu.cloud.auth.http.SAMLController
import dk.sdu.cloud.auth.processors.RefreshTokenProcessor
import dk.sdu.cloud.auth.processors.UserProcessor
import dk.sdu.cloud.auth.services.TokenService
import dk.sdu.cloud.auth.services.saml.validateOrThrow
import org.esciencecloud.service.*
import org.jetbrains.exposed.sql.Database
import java.security.interfaces.RSAPrivateKey
import java.util.*
import java.util.concurrent.TimeUnit

data class RequestAndRefreshToken(val accessToken: String, val refreshToken: String)
data class AccessToken(val accessToken: String)

data class KafkaConfiguration(val servers: List<String>)
data class DatabaseConfiguration(
        val url: String,
        val driver: String,
        val username: String,
        val password: String
)

data class AuthConfiguration(
        val kafka: KafkaConfiguration,
        val database: DatabaseConfiguration
)

class AuthServer(
        samlSettings: Properties,
        privKey: RSAPrivateKey,
        private val config: AuthConfiguration,
        private val hostname: String,
        private val port: Int = 42300
) {
    private val jwtAlg = Algorithm.RSA256(privKey)
    private val authSettings = SettingsBuilder().fromProperties(samlSettings).build().validateOrThrow()
    private lateinit var eventProducer: KafkaProducer<String, String>
    private lateinit var streams: KafkaStreams
    private lateinit var server: ApplicationEngine

    private fun retrieveKafkaStreamsConfiguration(): Properties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = "auth"
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = config.kafka.servers.joinToString(",")
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        this[StreamsConfig.APPLICATION_SERVER_CONFIG] = "$hostname:$port"
    }

    private fun retrieveKafkaProducerConfiguration(): Properties = Properties().apply {
        this[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = config.kafka.servers.joinToString(",")
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
    }

    fun start(wait: Boolean = true) {
        // TODO Service registration needs to be easier
        val serviceDefinition = ServiceDefinition("auth", Version.forIntegers(1, 0, 0))
        val instance = ServiceInstance(serviceDefinition, hostname, port)
        val (zk, node) = runBlocking {
            val zk = ZooKeeperConnection(listOf(ZooKeeperHostInfo("localhost"))).connect()
            val node = zk.registerService(instance,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE)

            Pair(zk, node)
        }

        Database.connect(
                url = config.database.url,
                driver = config.database.driver,

                user = config.database.username,
                password = config.database.password
        )

        val streamsBuilder = StreamsBuilder()
        eventProducer = KafkaProducer(retrieveKafkaProducerConfiguration())

        val tokenService = TokenService(jwtAlg, eventProducer)
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

        streams = KafkaStreams(streamsBuilder.build(), retrieveKafkaStreamsConfiguration())
        streams.setUncaughtExceptionHandler { _, _ -> stop() }
        streams.start()

        server.start(wait = wait)

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


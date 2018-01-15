package dk.sdu.cloud.tus

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.service.*
import dk.sdu.cloud.tus.api.TusServiceDescription
import io.ktor.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

private val log = LoggerFactory.getLogger("dk.sdu.cloud.project.MainKt")
typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine

class KafkaServices(
        private val streamsConfig: Properties,
        val producer: KafkaProducer<String, String>
) {
    fun build(block: Topology): KafkaStreams {
        return KafkaStreams(block, streamsConfig)
    }
}

data class ICatDatabaseConfig(
        val jdbcUrl: String,
        val user: String,
        val password: String,
        val defaultZone: String
)

data class Configuration(
        private val connection: RawConnectionConfig,
        val database: ICatDatabaseConfig,
        val appDatabaseUrl: String, // TODO This should be fixed
        val refreshToken: String
) {
    @get:JsonIgnore
    val connConfig: ConnectionConfig get() = connection.processed

    internal fun configure() {
        connection.configure(TusServiceDescription, 42400)
    }
}

fun main(args: Array<String>) {
    val configuration = run {
        log.info("Reading configuration...")
        val configMapper = jacksonObjectMapper()
        val configFilePath = args.getOrNull(0) ?: "/etc/${TusServiceDescription.name}/config.json"
        val configFile = File(configFilePath)
        log.debug("Using path: $configFilePath. This has resolved to: ${configFile.absolutePath}")
        if (!configFile.exists()) {
            throw IllegalStateException("Unable to find configuration file. Attempted to locate it at: " +
                    configFile.absolutePath)
        }

        configMapper.readValue<Configuration>(configFile).also {
            it.configure()
            log.info("Retrieved the following configuration:")
            log.info(it.toString())
        }
    }

    val kafka = run {
        log.info("Connecting to Kafka")
        val streamsConfig = KafkaUtil.retrieveKafkaStreamsConfiguration(configuration.connConfig).apply {
            this[StreamsConfig.STATE_DIR_CONFIG] = File("kafka-streams").absolutePath
        }

        val producer = run {
            val kafkaProducerConfig = KafkaUtil.retrieveKafkaProducerConfiguration(configuration.connConfig)
            KafkaProducer<String, String>(kafkaProducerConfig)
        }

        log.info("Connected to Kafka")
        KafkaServices(streamsConfig, producer)
    }

    log.info("Connecting to Zookeeper")
    val zk = runBlocking { ZooKeeperConnection(configuration.connConfig.zookeeper.servers).connect() }
    log.info("Connected to Zookeeper")

    val cloud = RefreshingJWTAuthenticator(DirectServiceClient(zk), configuration.refreshToken)

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(CIO, port = configuration.connConfig.service.port, module = block)
    }

    Database.connect(
            url = configuration.appDatabaseUrl,
            driver = "org.postgresql.Driver",
            user = configuration.database.user,
            password = configuration.database.password
    )

    val server = Server(configuration, kafka, zk, serverProvider, cloud)
    server.start()
}

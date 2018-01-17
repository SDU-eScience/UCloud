package dk.sdu.cloud.project

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.project.api.ProjectServiceDescription
import dk.sdu.cloud.service.*
import io.ktor.application.Application
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.Topology
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

private val log = LoggerFactory.getLogger("dk.sdu.cloud.project.MainKt")
internal typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine

class Configuration(
        private val connection: RawConnectionConfig,
        val refreshToken: String,
        val database: DatabaseConfiguration
) {
    @get:JsonIgnore
    val connConfig: ConnectionConfig
        get() = connection.processed

    internal fun configure() {
        connection.configure(ProjectServiceDescription, 42500)
    }
}

class KafkaServices(
        private val streamsConfig: Properties,
        val producer: KafkaProducer<String, String>
) {
    fun build(block: Topology): KafkaStreams {
        return KafkaStreams(block, streamsConfig)
    }
}

fun main(args: Array<String>) {
    val configuration = run {
        log.info("Reading configuration...")
        val configMapper = jacksonObjectMapper()
        val configFilePath = args.getOrNull(0) ?: "/etc/${ProjectServiceDescription.name}/config.json"
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
        val streamsConfig = KafkaUtil.retrieveKafkaStreamsConfiguration(configuration.connConfig)
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

    log.info("Connecting to database")
    // TODO I'm pretty sure we only have to do this once.
    // But if we end up with exceptions then that is probably not true.
    Database.connect(
            url = configuration.database.url,
            driver = configuration.database.driver,

            user = configuration.database.username,
            password = configuration.database.password
    )
    log.info("Connected to database")

    val cloud = RefreshingJWTAuthenticator(DirectServiceClient(zk), configuration.refreshToken)

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(Netty, port = configuration.connConfig.service.port, module = block)
    }

    val server = Server(configuration, kafka, zk, serverProvider, cloud)
    server.start()
}
package dk.sdu.cloud.app

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.AppServiceDescription
import dk.sdu.cloud.app.services.ssh.SimpleSSHConfig
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.ext.irods.IRodsConnectionInformation
import dk.sdu.cloud.storage.ext.irods.IRodsStorageConnectionFactory
import io.ktor.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.Topology
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

data class DatabaseConfiguration(
        val url: String,
        val driver: String,
        val username: String,
        val password: String
)

data class HPCConfig(
        private val connection: RawConnectionConfig,
        val ssh: SimpleSSHConfig,
        val storage: StorageConfiguration,
        val rpc: RPCConfiguration,
        val refreshToken: String,
        val database: DatabaseConfiguration
) {
    @get:JsonIgnore
    val connConfig: ConnectionConfig
        get() = connection.processed

    internal fun configure() {
        connection.configure(AppServiceDescription, 42200)
    }
}

data class StorageConfiguration(val host: String, val port: Int, val zone: String)
data class RPCConfiguration(val secretToken: String)

private val log = LoggerFactory.getLogger("dk.sdu.cloud.project.MainKt")

// TODO Likely candidate for extraction into service-common
internal typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine
class KafkaServices(
        private val streamsConfig: Properties,
        val producer: KafkaProducer<String, String>
) {
    fun build(block: Topology): KafkaStreams {
        return KafkaStreams(block, streamsConfig)
    }
}

fun main(args: Array<String>) {
    val serviceDescription = AppServiceDescription

    val configuration = run {
        log.info("Reading configuration...")
        val configMapper = jacksonObjectMapper()
        val configFilePath = args.getOrNull(0) ?: "/etc/${serviceDescription.name}/config.json"
        val configFile = File(configFilePath)
        log.debug("Using path: $configFilePath. This has resolved to: ${configFile.absolutePath}")
        if (!configFile.exists()) {
            throw IllegalStateException("Unable to find configuration file. Attempted to locate it at: " +
                    configFile.absolutePath)
        }

        configMapper.readValue<HPCConfig>(configFile).also {
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
    Database.connect(
            url = configuration.database.url,
            driver = configuration.database.driver,

            user = configuration.database.username,
            password = configuration.database.password
    )
    log.info("Connected to database")

    val irodsConnectionFactory = IRodsStorageConnectionFactory(IRodsConnectionInformation(
            host = configuration.storage.host,
            zone = configuration.storage.zone,
            port = configuration.storage.port,
            storageResource = "radosRandomResc",
            sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REQUIRE,
            authScheme = AuthScheme.PAM
    ))

    val cloud = RefreshingJWTAuthenticator(DirectServiceClient(zk), configuration.refreshToken)
    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(CIO, port = configuration.connConfig.service.port, module = block)
    }

    val server = Server(kafka, zk, cloud, configuration, serverProvider, irodsConnectionFactory)
    server.start()
}


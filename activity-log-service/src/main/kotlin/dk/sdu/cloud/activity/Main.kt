package dk.sdu.cloud.activity

import dk.sdu.cloud.activity.api.ActivityServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.service.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.StreamsConfig
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.io.File

private val log = LoggerFactory.getLogger("dk.sdu.cloud.activity.MainKt")

data class Configuration(
    private val connection: RawConnectionConfig,
    val refreshToken: String,
    val database: DatabaseConfiguration
) : ServerConfiguration {
    override val connConfig: ConnectionConfig = connection.processed

    override fun configure() {
        connection.configure(ActivityServiceDescription, 42900)
    }
}

fun main(args: Array<String>) {
    val configuration = readConfigurationBasedOnArgs<Configuration>(args, ActivityServiceDescription, log)
    val kafka = KafkaUtil.createKafkaServices(
        configuration,
        log = log,

        streamsConfigBody = {
            it[StreamsConfig.STATE_DIR_CONFIG] = File("kafka-streams").absolutePath
        }
    )

    log.info("Connecting to Zookeeper")
    val zk = runBlocking { ZooKeeperConnection(configuration.connConfig.zookeeper.servers).connect() }
    log.info("Connected to Zookeeper")

    val cloud = RefreshingJWTAuthenticator(DirectServiceClient(zk), configuration.refreshToken)

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(CIO, port = configuration.connConfig.service.port, module = block)
    }

    Database.connect(
        url = configuration.database.url,
        driver = configuration.database.driver,
        user = configuration.database.username,
        password = configuration.database.password
    )

    val server = Server(configuration, kafka, zk, serverProvider, cloud)
    server.start()
}
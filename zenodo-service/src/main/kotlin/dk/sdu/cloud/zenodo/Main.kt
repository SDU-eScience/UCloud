package dk.sdu.cloud.zenodo

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.zenodo.api.ZenodoServiceDescription
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory

data class Configuration(
    private val connection: RawConnectionConfig,
    val refreshToken: String,
    val zenodo: ZenodoAPIConfiguration
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig: ConnectionConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(ZenodoServiceDescription, 42250)
    }
}

data class ZenodoAPIConfiguration(
    val clientId: String,
    val clientSecret: String
)

private val log = LoggerFactory.getLogger("dk.sdu.cloud.zenodo.MainKt")

fun main(args: Array<String>) {
    val serviceDescription = ZenodoServiceDescription

    val configuration = readConfigurationBasedOnArgs<Configuration>(args, serviceDescription, log = log)
    val kafka = KafkaUtil.createKafkaServices(configuration, log = log)

    log.info("Connecting to Zookeeper")
    val zk = runBlocking { ZooKeeperConnection(configuration.connConfig.zookeeper.servers).connect() }
    log.info("Connected to Zookeeper")

    val cloud = if (args.getOrNull(1) == "dev")
        RefreshingJWTAuthenticator(SDUCloud("https://cloud.sdu.dk"), configuration.refreshToken)
    else
        RefreshingJWTAuthenticator(DirectServiceClient(zk), configuration.refreshToken)

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(CIO, port = configuration.connConfig.service.port, module = block)
    }

    Server(cloud, kafka, zk, configuration, serverProvider).start()
}
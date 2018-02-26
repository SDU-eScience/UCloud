package dk.sdu.cloud.zenodo

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.zenodo.api.ZenodoServiceDescription
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory

data class Configuration(
    private val connection: RawConnectionConfig,
    val refreshToken: String,
    val zenodo: ZenodoAPIConfiguration,
    val production: Boolean
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

    log.info("Connecting to Service Registry!")
    val serviceRegistry = ServiceRegistry(serviceDescription.instance(configuration.connConfig))
    log.info("Connected to Service Registry!")

    val cloud = if (args.getOrNull(1) == "dev")
        RefreshingJWTAuthenticator(SDUCloud("https://cloud.sdu.dk"), configuration.refreshToken)
    else
        RefreshingJWTAuthenticator(DirectServiceClient(serviceRegistry), configuration.refreshToken)

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(Netty, port = configuration.connConfig.service.port, module = block)
    }

    Server(cloud, kafka, serviceRegistry, configuration, serverProvider).start()
}
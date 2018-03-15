package dk.sdu.cloud.app

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.app.api.AppServiceDescription
import dk.sdu.cloud.app.services.ssh.SimpleSSHConfig
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.ext.irods.IRodsConnectionInformation
import dk.sdu.cloud.storage.ext.irods.IRodsStorageConnectionFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

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
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig: ConnectionConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(AppServiceDescription, 42200)
    }
}

data class StorageConfiguration(val host: String, val port: Int, val zone: String)
data class RPCConfiguration(val secretToken: String)

private val log = LoggerFactory.getLogger("dk.sdu.cloud.project.MainKt")

fun main(args: Array<String>) {
    val serviceDescription = AppServiceDescription

    val configuration = readConfigurationBasedOnArgs<HPCConfig>(args, serviceDescription, log = log)
    val kafka = KafkaUtil.createKafkaServices(configuration, log = log)

    log.info("Connecting to Service Registry")
    val serviceRegistry = ServiceRegistry(serviceDescription.instance(configuration.connConfig))
    log.info("Connected to Service Registry")

    log.info("Connecting to database")
    Database.connect(
        url = configuration.database.url,
        driver = configuration.database.driver,

        user = configuration.database.username,
        password = configuration.database.password
    )
    log.info("Connected to database")

    val irodsConnectionFactory = IRodsStorageConnectionFactory(
        IRodsConnectionInformation(
            host = configuration.storage.host,
            zone = configuration.storage.zone,
            port = configuration.storage.port,
            storageResource = "radosRandomResc",
            sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REQUIRE,
            authScheme = AuthScheme.PAM
        )
    )

    val cloud = RefreshingJWTAuthenticator(defaultServiceClient(args, serviceRegistry), configuration.refreshToken)
    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(Netty, port = configuration.connConfig.service.port, module = block)
    }

    val server = Server(kafka, serviceRegistry, cloud, configuration, serverProvider, irodsConnectionFactory)
    server.start()
}


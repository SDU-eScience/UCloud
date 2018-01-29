package dk.sdu.cloud.storage

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.ext.irods.IRodsConnectionInformation
import dk.sdu.cloud.storage.ext.irods.IRodsStorageConnectionFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.slf4j.LoggerFactory

data class Configuration(
    val storage: StorageConfiguration,
    private val connection: RawConnectionConfig,
    val refreshToken: String
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig: ConnectionConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(StorageServiceDescription, 42000)
    }
}

data class StorageConfiguration(
    val host: String,
    val port: Int,
    val zone: String,
    val resource: String,
    val authScheme: String?,
    val sslPolicy: String?
)

private val log = LoggerFactory.getLogger("dk.sdu.cloud.storage.MainKt")
fun main(args: Array<String>) {
    log.info("Starting storage service")

    val configuration = readConfigurationBasedOnArgs<Configuration>(args, StorageServiceDescription, log = log)
    val kafka = KafkaUtil.createKafkaServices(configuration, log = log)

    log.info("Connecting to Zookeeper")
    val zk = runBlocking { ZooKeeperConnection(configuration.connConfig.zookeeper.servers).connect() }
    log.info("Connected to Zookeeper")

    val storageService = with(configuration.storage) {
        IRodsStorageConnectionFactory(
            IRodsConnectionInformation(
                host = host,
                port = port,
                zone = zone,
                storageResource = resource,
                authScheme = if (authScheme != null) AuthScheme.valueOf(authScheme) else AuthScheme.STANDARD,
                sslNegotiationPolicy =
                if (sslPolicy != null)
                    ClientServerNegotiationPolicy.SslNegotiationPolicy.valueOf(sslPolicy)
                else
                    ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
            )
        )
    }

//    val cloud = RefreshingJWTAuthenticator(DirectServiceClient(zk), configuration.refreshToken)
    val cloud = RefreshingJWTAuthenticator(SDUCloud("https://cloud.sdu.dk"), configuration.refreshToken)
    val adminAccount = run {
        val currentAccessToken = cloud.retrieveTokenRefreshIfNeeded()
        storageService.createForAccount("_storage", currentAccessToken).orThrow()
    }

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(Netty, port = configuration.connConfig.service.port, module = block)
    }

    Server(configuration, storageService, adminAccount, kafka, serverProvider, zk, cloud).start()
}

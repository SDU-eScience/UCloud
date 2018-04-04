package dk.sdu.cloud.storage

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.services.ext.irods.ICATDatabaseConfig
import dk.sdu.cloud.storage.services.ext.irods.IRodsConnectionInformation
import dk.sdu.cloud.storage.services.ext.irods.IRodsStorageConnectionFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

data class Configuration(
    val storage: StorageConfiguration,
    val icat: ICATDatabaseConfig,
    private val connection: RawConnectionConfig,
    val appDatabaseUrl: String, // TODO This should be fixed
    val refreshToken: String,
    val consulHostname: String = "localhost"
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

    log.info("Connecting to Service Registry")
    val serviceRegistry = ServiceRegistry(
        StorageServiceDescription.instance(configuration.connConfig),
        Consul.builder()
            .withHostAndPort(HostAndPort.fromHost(configuration.consulHostname).withDefaultPort(8500))
            .build()
    )
    log.info("Connected to Service Registry")

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

    val cloud = RefreshingJWTAuthenticatedCloud(
        defaultServiceClient(args, serviceRegistry),
        configuration.refreshToken
    )

    Database.connect(
        url = configuration.appDatabaseUrl,
        driver = "org.postgresql.Driver",
        user = configuration.icat.user,
        password = configuration.icat.password
    )

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(Netty, port = configuration.connConfig.service.port, module = block)
    }

    Server(configuration, storageService, kafka, serverProvider, serviceRegistry, cloud, args).start()
}

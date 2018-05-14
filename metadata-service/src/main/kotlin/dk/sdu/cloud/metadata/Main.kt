package dk.sdu.cloud.metadata

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.metadata.api.MetadataServiceDescription
import dk.sdu.cloud.service.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

data class Configuration(
    private val connection: RawConnectionConfig,
    val refreshToken: String
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig: ConnectionConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(MetadataServiceDescription, 43100)
    }

    override fun toString(): String {
        return "Configuration(connection=$connection)"
    }
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.metadata.MainKt")

fun main(args: Array<String>) {
    log.info("Starting storage service")

    val configuration = readConfigurationBasedOnArgs<Configuration>(args, MetadataServiceDescription, log = log)
    val kafka = KafkaUtil.createKafkaServices(configuration, log = log)

    log.info("Connecting to Service Registry")
    val serviceRegistry = ServiceRegistry(
        MetadataServiceDescription.instance(configuration.connConfig),
        Consul.builder()
            .withHostAndPort(HostAndPort.fromHost("localhost").withDefaultPort(8500))
            .build()
    )
    log.info("Connected to Service Registry")

    val cloud = RefreshingJWTAuthenticatedCloud(
        defaultServiceClient(args, serviceRegistry),
        configuration.refreshToken
    )

    val engine = Netty
    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(engine, port = configuration.connConfig.service.port, module = block)
    }

    log.info("Using engine: ${engine.javaClass.simpleName}")

    Server(configuration, kafka, serverProvider, serviceRegistry, cloud, args).start()
}

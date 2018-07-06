package dk.sdu.cloud.storage

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.storage.api.StorageServiceDescription
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

data class Configuration(
    private val connection: RawConnectionConfig,
    val refreshToken: String,
    val consulHostname: String = "localhost"
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig: ConnectionConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(StorageServiceDescription, 42000)
    }

    override fun toString(): String {
        return "Configuration(connection=$connection, consulHostname='$consulHostname')"
    }
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.storage.MainKt")

fun main(args: Array<String>) {
    log.info("Starting storage service")

    val serviceDescription = StorageServiceDescription
    val configuration = readConfigurationBasedOnArgs<Configuration>(args, serviceDescription, log = log)
    val kafka = KafkaUtil.createKafkaServices(configuration, log = log)

    log.info("Connecting to Service Registry")
    val serviceRegistry = ServiceRegistry(
        StorageServiceDescription.instance(configuration.connConfig),
        Consul.builder()
            .withHostAndPort(HostAndPort.fromHost(configuration.consulHostname).withDefaultPort(8500))
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

    val isDevelopment = args.contains("--dev")

    log.info("Initializing database")
    val db = with(configuration.connConfig.database!!) {
        HibernateSessionFactory.create(
            HibernateDatabaseConfig(
                POSTGRES_DRIVER,
                postgresJdbcUrl(host, database),
                POSTGRES_9_5_DIALECT,
                username,
                password,
                defaultSchema = serviceDescription.name,
                validateSchemaOnStartup = !args.contains("--generate-ddl")
            )
        )
    }
    log.info("Database initialized")

    if (args.contains("--generate-ddl")) {
        println(db.generateDDL())
        db.close()
    } else {
        Server(configuration, kafka, serverProvider, db, serviceRegistry, cloud, args).start()
    }
}

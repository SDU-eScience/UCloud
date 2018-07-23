package dk.sdu.cloud.metadata

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.net.HostAndPort
import com.orbitz.consul.Consul
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.metadata.api.MetadataServiceDescription
import dk.sdu.cloud.metadata.services.ProjectHibernateDAO
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

data class ElasticConfiguration(
    val hostname: String,
    val port: Int = 9200,
    val scheme: String = "http"
)

data class Configuration(
    private val connection: RawConnectionConfig,
    val refreshToken: String,
    val elastic: ElasticConfiguration

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

private const val ARG_GENERATE_DDL = "--generate-ddl"
private const val ARG_MIGRATE = "--migrate"

private val log = LoggerFactory.getLogger("dk.sdu.cloud.metadata.MainKt")

fun main(args: Array<String>) {
    log.info("Starting storage service")

    val serviceDescription = MetadataServiceDescription
    val configuration = readConfigurationBasedOnArgs<Configuration>(args, serviceDescription, log = log)
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

    val jdbcUrl = with(configuration.connConfig.database!!) { postgresJdbcUrl(host, database) }
    val db = with(configuration.connConfig.database!!) {
        HibernateSessionFactory.create(
            HibernateDatabaseConfig(
                POSTGRES_DRIVER,
                jdbcUrl,
                POSTGRES_9_5_DIALECT,
                username,
                password,
                defaultSchema = serviceDescription.name,
                validateSchemaOnStartup = !args.contains(ARG_GENERATE_DDL) && !args.contains(ARG_MIGRATE)
            )
        )
    }
    log.info("Database initialized")
    when {
        args.contains("--generate-ddl") -> {
            println(db.generateDDL())
            db.close()
        }

        args.contains("--migrate") -> {
            val flyway = Flyway()
            with(configuration.connConfig.database!!) {
                flyway.setDataSource(jdbcUrl, username, password)
            }
            flyway.setSchemas(serviceDescription.name)
            flyway.migrate()
        }

        else -> {
            Server(db, configuration, kafka, serverProvider, serviceRegistry, cloud, args).start()
        }
    }
}

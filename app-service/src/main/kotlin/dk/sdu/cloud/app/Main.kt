package dk.sdu.cloud.app

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.app.api.AppServiceDescription
import dk.sdu.cloud.app.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.services.ssh.SimpleSSHConfig
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

data class HPCConfig(
    private val connection: RawConnectionConfig,
    val ssh: SimpleSSHConfig,
    val storage: StorageConfiguration,
    val rpc: RPCConfiguration,
    val refreshToken: String
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig: ConnectionConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(AppServiceDescription, 42200)
    }

    override fun toString(): String {
        return "HPCConfig(connection=$connection, ssh=$ssh, storage=$storage, rpc=$rpc)"
    }
}

data class StorageConfiguration(val host: String, val port: Int, val zone: String)
data class RPCConfiguration(val secretToken: String) {
    override fun toString(): String = "RPCConfiguration(xxxx)"
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.project.MainKt")

private const val ARG_GENERATE_DDL = "--generate-ddl"
private const val ARG_MIGRATE = "--migrate"

fun main(args: Array<String>) {
    val serviceDescription = AppServiceDescription

    val configuration = readConfigurationBasedOnArgs<HPCConfig>(args, serviceDescription, log = log)
    val kafka = KafkaUtil.createKafkaServices(configuration, log = log)

    log.info("Connecting to Service Registry")
    val serviceRegistry = ServiceRegistry(serviceDescription.instance(configuration.connConfig))
    log.info("Connected to Service Registry")

    log.info("Connecting to database")
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
    log.info("Connected to database")

    val cloud = RefreshingJWTAuthenticatedCloud(
        defaultServiceClient(args, serviceRegistry),
        configuration.refreshToken
    )
    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(Netty, port = configuration.connConfig.service.port, module = block)
    }

    when {
        args.contains(ARG_GENERATE_DDL) -> {
            println(db.generateDDL())
            db.close()
        }

        args.contains(ARG_MIGRATE) -> {
            val flyway = Flyway()
            with(configuration.connConfig.database!!) {
                flyway.setDataSource(jdbcUrl, username, password)
            }
            flyway.setSchemas(serviceDescription.name)
            flyway.migrate()
        }

        else -> {
            val server = Server(kafka, serviceRegistry, cloud, configuration, serverProvider, db)
            server.start()
        }
    }
}


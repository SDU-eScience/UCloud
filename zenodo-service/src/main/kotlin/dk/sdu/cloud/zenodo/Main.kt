package dk.sdu.cloud.zenodo

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.zenodo.api.ZenodoServiceDescription
import dk.sdu.cloud.zenodo.services.hibernate.PublicationHibernateDAO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.flywaydb.core.Flyway
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

    override fun toString(): String {
        return "Configuration(connection=$connection, zenodo=$zenodo, production=$production)"
    }
}

data class ZenodoAPIConfiguration(
    val clientId: String,
    val clientSecret: String
) {
    override fun toString(): String {
        return "ZenodoAPIConfiguration()"
    }
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.zenodo.MainKt")

private const val ARG_GENERATE_DDL = "--generate-ddl"
private const val ARG_MIGRATE = "--migrate"

fun main(args: Array<String>) {
    val serviceDescription = ZenodoServiceDescription

    val configuration = readConfigurationBasedOnArgs<Configuration>(args, serviceDescription, log = log)
    val kafka = KafkaUtil.createKafkaServices(configuration, log = log)

    log.info("Connecting to Service Registry!")
    val serviceRegistry = ServiceRegistry(serviceDescription.instance(configuration.connConfig))
    log.info("Connected to Service Registry!")

    val parent = defaultServiceClient(args, serviceRegistry)
    if (parent is DirectServiceClient) {
        // TODO Temporary work around
        parent.overrideServiceVersionPreference("storage", "*")
    }

    val cloud = RefreshingJWTAuthenticatedCloud(
        parent,
        configuration.refreshToken
    )

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(Netty, port = configuration.connConfig.service.port, module = block)
    }

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
                validateSchemaOnStartup = !args.contains(ARG_GENERATE_DDL) && !args.contains(ARG_MIGRATE),
                showSQLInStdout = true
            )
        )
    }
    log.info("Connected to database")

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
            Server(db, cloud, kafka, serviceRegistry, configuration, serverProvider).start()
        }
    }

}
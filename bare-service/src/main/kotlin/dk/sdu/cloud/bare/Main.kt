package dk.sdu.cloud.bare

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.bare.api.BareServiceDescription
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.PreparedRESTCall
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.service.*
import org.slf4j.LoggerFactory
import java.net.ConnectException

data class Configuration(
    private val connection: RawConnectionConfig,
    val refreshToken: String
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(BareServiceDescription, 8080)
    }

    override fun toString() = connection.toString()
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.bare.MainKt")
private const val ARG_GENERATE_DDL = "--generate-ddl"
private const val ARG_MIGRATE = "--migrate"

fun main(args: Array<String>) {
    val serviceDescription = BareServiceDescription
//    val configuration = readConfigurationBasedOnArgs<Configuration>(args, serviceDescription, log = log)
    val configuration = Configuration(
        RawConnectionConfig(
            kafka = KafkaConnectionConfig(
                listOf(
//                KafkaHostConfig("kafka-kafka.kafka.svc.cluster.local")
                    KafkaHostConfig("localhost")
                )
            ),
            service = null,
            database = null
        ),
        "not-a-real-token"
    ).also { it.configure() }
    val kafka = KafkaUtil.createKafkaServices(configuration, log = log)

    val micro = Micro().apply {
        init(BareServiceDescription, args)
        install(KtorServerProviderFeature)
    }

    /*
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
    */

//    val cloud = RefreshingJWTAuthenticatedCloud(
//        K8CloudContext(),
//        configuration.refreshToken
//    )

    val cloud = JWTAuthenticatedCloud(
        K8CloudContext(),
        "not-a-real-token"
    )

    /*
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
    */
    val server = Server(kafka, cloud, configuration, micro.serverProvider)
    server.start()
//        }
//    }
}

class K8CloudContext : CloudContext {
    override fun resolveEndpoint(call: PreparedRESTCall<*, *>): String {
        return resolveEndpoint(call.owner)
    }

    override fun resolveEndpoint(service: ServiceDescription): String {
        return "http://${service.name}:8080"
    }

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        return false
    }
}
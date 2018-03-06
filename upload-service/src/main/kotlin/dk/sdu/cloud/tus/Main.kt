package dk.sdu.cloud.tus

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.ext.irods.ICatDatabaseConfig
import dk.sdu.cloud.tus.api.TusServiceDescription
import io.ktor.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import org.apache.kafka.streams.StreamsConfig
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.io.File

private val log = LoggerFactory.getLogger("dk.sdu.cloud.project.MainKt")
typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine

data class Configuration(
    private val connection: RawConnectionConfig,
    val database: ICatDatabaseConfig,
    val appDatabaseUrl: String, // TODO This should be fixed
    val refreshToken: String
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig: ConnectionConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(TusServiceDescription, 42400)
    }
}

fun main(args: Array<String>) {
    val configuration = readConfigurationBasedOnArgs<Configuration>(args, TusServiceDescription, log)
    val kafka = KafkaUtil.createKafkaServices(
        configuration,
        log = log,

        streamsConfigBody = {
            it[StreamsConfig.STATE_DIR_CONFIG] = File("kafka-streams").absolutePath
        }
    )

    log.info("Connecting to Service Registry")
    val serviceRegistry = ServiceRegistry(TusServiceDescription.instance(configuration.connConfig))
    log.info("Connected to Service Registry")

    val cloud = RefreshingJWTAuthenticator(DirectServiceClient(serviceRegistry), configuration.refreshToken)

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(CIO, port = configuration.connConfig.service.port, module = block)
    }

    Database.connect(
        url = configuration.appDatabaseUrl,
        driver = "org.postgresql.Driver",
        user = configuration.database.user,
        password = configuration.database.password
    )

    val shouldBench = args.contains("--bench")

    val server = Server(configuration, kafka, serviceRegistry, serverProvider, cloud, shouldBench)
    server.start()
}

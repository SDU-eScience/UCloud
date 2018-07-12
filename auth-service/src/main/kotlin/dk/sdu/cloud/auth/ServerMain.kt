package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.onelogin.saml2.settings.SettingsBuilder
import com.onelogin.saml2.util.Util
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.services.PersonUtils
import dk.sdu.cloud.auth.services.UserHibernateDAO
import dk.sdu.cloud.auth.services.saml.KtorUtils
import dk.sdu.cloud.auth.services.saml.validateOrThrow
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import io.ktor.application.Application
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.apache.kafka.clients.producer.KafkaProducer
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.io.File
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

private val log = LoggerFactory.getLogger("dk.sdu.cloud.auth.ServerMainKt")

private fun loadKeysAndInsertIntoProps(properties: Properties): Pair<RSAPublicKey, RSAPrivateKey> {
    val certs =
        File("certs").takeIf { it.exists() && it.isDirectory } ?: throw IllegalStateException("Missing 'certs' folder")
    val x509Cert = File(certs, "cert.pem").takeIf { it.exists() && it.isFile }
            ?: throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name cert.pem")
    val privateKey = File(certs, "key.pem").takeIf { it.exists() && it.isFile }
            ?: throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name key.pem")

    val x509Text = x509Cert.readText()
    val privText = privateKey.readText()
    properties["onelogin.saml2.sp.x509cert"] = x509Text
    properties["onelogin.saml2.sp.privatekey"] = privText

    val loadedX509Cert = Util.loadCert(x509Text)
    val loadedPrivKey = Util.loadPrivateKey(privText)

    return Pair(
        loadedX509Cert.publicKey as RSAPublicKey,
        loadedPrivKey as RSAPrivateKey
    )
}

internal typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine

data class AuthConfiguration(
    val enablePasswords: Boolean = true,
    val enableWayf: Boolean = false,
    val production: Boolean = true,
    private val connection: RawConnectionConfig
) {
    @get:JsonIgnore
    val connConfig: ConnectionConfig
        get() = connection.processed

    internal fun configure() {
        connection.configure(AuthServiceDescription, 42300)
    }
}

private const val ARG_GENERATE_DDL = "--generate-ddl"
private const val ARG_MIGRATE = "--migrate"

fun main(args: Array<String>) {
    val serviceDescription = AuthServiceDescription

    val configuration = run {
        log.info("Reading configuration...")
        val configMapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        val configFilePath = args.getOrNull(0) ?: "/etc/${serviceDescription.name}/config.json"
        val configFile = File(configFilePath)
        log.debug("Using path: $configFilePath. This has resolved to: ${configFile.absolutePath}")
        if (!configFile.exists()) {
            throw IllegalStateException(
                "Unable to find configuration file. Attempted to locate it at: " +
                        configFile.absolutePath
            )
        }

        configMapper.readValue<AuthConfiguration>(configFile).also {
            it.configure()
            log.info("Retrieved the following configuration:")
            log.info(it.toString())
        }
    }

    val kafka = run {
        log.info("Connecting to Kafka")
        val streamsConfig = KafkaUtil.retrieveKafkaStreamsConfiguration(configuration.connConfig)
        val producer = run {
            val kafkaProducerConfig = KafkaUtil.retrieveKafkaProducerConfiguration(configuration.connConfig)
            KafkaProducer<String, String>(kafkaProducerConfig)
        }

        log.info("Connected to Kafka")
        KafkaServices(streamsConfig, producer)
    }

    log.info("Connecting to database...")
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
    log.info("Connected to database!")

    KtorUtils.runningInProduction = configuration.production

    log.info("Connecting to Service Registry")
    val serviceRegistry = ServiceRegistry(serviceDescription.instance(configuration.connConfig))
    log.info("Connected to Service Registry")

    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(Netty, port = configuration.connConfig.service.port, module = block)
    }

    val samlProperties = Properties().apply {
        load(AuthServer::class.java.classLoader.getResourceAsStream("saml.properties"))
    }

    val (_, priv) = loadKeysAndInsertIntoProps(samlProperties)
    val authSettings = SettingsBuilder().fromProperties(samlProperties).build().validateOrThrow()

    // TODO FIXME!!!
    val cloud = RefreshingJWTAuthenticatedCloud(defaultServiceClient(args, serviceRegistry), "TODO")

    when {
        args.contains(ARG_GENERATE_DDL) -> {
            println("Start of DDL")
            println(db.generateDDL())
            db.close()
            println("End of DDL")
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
            AuthServer(
                db,
                jwtAlg = Algorithm.RSA256(null, priv),
                config = configuration,
                authSettings = authSettings,
                serviceRegistry = serviceRegistry,
                kafka = kafka,
                ktor = serverProvider,
                cloud = cloud
            ).start()
        }
    }
}

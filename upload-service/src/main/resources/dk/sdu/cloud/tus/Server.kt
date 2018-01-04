package dk.sdu.cloud.tus

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.service.*
import dk.sdu.cloud.tus.api.TusServiceDescription
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.AttributeKey
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

private val log = LoggerFactory.getLogger("dk.sdu.cloud.tus.Server")
private val mapper = jacksonObjectMapper()

data class ICatDatabaseConfig(
        val jdbcUrl: String,
        val user: String,
        val password: String,
        val defaultZone: String
)

data class Configuration(
        private val connection: RawConnectionConfig,
        val database: ICatDatabaseConfig
) {
    val connConfig: ConnectionConfig get() = connection.processed

    internal fun configure() {
        connection.configure(TusServiceDescription, 42400)
    }
}

private val ApplicationRequest.bearer: String?
    get() {
        val header = header(HttpHeaders.Authorization) ?: return null
        if (!header.startsWith("Bearer ")) {
            log.debug("Invalid prefix in authorization header: $header")
            return null
        }
        return header.substringAfter("Bearer ")
    }

private val JwtKey = AttributeKey<DecodedJWT>("JWT")
private val JobIdKey = AttributeKey<String>("job-id")

val ApplicationRequest.validatedPrincipal: DecodedJWT get() = call.attributes[JwtKey]
val ApplicationRequest.jobId: String get() = call.attributes[JobIdKey]

fun main(args: Array<String>) {
    log.info("Starting server!")

    val configuration = run {
        // Load configuration from file. Can use first argument to program or automatically look at /etc/tus/config.json
        val configFile = (args.firstOrNull() ?: "/etc/tus/config.json").let { File(it) }

        log.debug("Reading configuration from ${configFile.absolutePath}")
        if (!configFile.exists()) {
            throw IllegalStateException("Unable to locate configuration file. Attempted to locate it " +
                    "at ${configFile.absolutePath}")
        }

        mapper.readValue<Configuration>(configFile).also { it.configure() }
    }

    log.info("Configuration successfully loaded.")
    log.debug("Loaded configuration: $configuration")

    log.info("Connecting to Zookeeper")
    val instance = TusServiceDescription.instance(configuration.connConfig)
    val (zk, node) = runBlocking {
        val zk = ZooKeeperConnection(configuration.connConfig.zookeeper.servers).connect()
        log.debug("Registering service")
        val node = zk.registerService(instance)
        log.debug("Got back node: $node")
        Pair(zk, node)
    }

    log.info("Creating services")
    val tus = TusController(configuration.database)

    log.info("Preparing HTTP server")
    val server = embeddedServer(Netty, port = configuration.connConfig.service.port) {
        install(CallLogging)
        install(DefaultHeaders)
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
            }
        }

        routing {
            intercept(ApplicationCallPipeline.Infrastructure) {
                val token = call.request.bearer ?: run {
                    log.debug("Did not receive a bearer token in the header of the request!")
                    call.respond(HttpStatusCode.Unauthorized)
                    finish()
                    return@intercept
                }

                val uuid = call.request.headers["Job-Id"] ?: run {
                    log.debug("Did not receive a valid Job-Id in the header of the request!")
                    call.respond(HttpStatusCode.BadRequest) // TODO
                    finish()
                    return@intercept
                }

                val validated = TokenValidation.validateOrNull(token) ?: run {
                    log.debug("The following token did not pass validation: $token")
                    call.respond(HttpStatusCode.Unauthorized)
                    finish()
                    return@intercept
                }

                call.attributes.put(JwtKey, validated)
                call.attributes.put(JobIdKey, uuid)
            }

            route("api") {
                route("tus") {
                    tus.registerTusEndpoint(this, "/api/tus")
                }
            }
        }
    }

    log.info("Starting server")
    server.start()

    log.info("Marking service as ready in Zookeeper")
    runBlocking { zk.markServiceAsReady(node, instance) }
    log.info("Server is ready!")
}
package dk.sdu.cloud.web

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dk.sdu.cloud.service.*
import dk.sdu.cloud.web.api.WebServiceDescription
import freemarker.cache.ClassTemplateLoader
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.freemarker.FreeMarker
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.util.escapeHTML
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory

data class Configuration(
    private val connection: RawConnectionConfig
) : ServerConfiguration {
    @get:JsonIgnore
    override val connConfig: ConnectionConfig
        get() = connection.processed

    override fun configure() {
        connection.configure(WebServiceDescription, 42900)
    }
}

class Main {
    fun Application.module() {
        val serviceDescription = WebServiceDescription

        val configuration = readConfigurationBasedOnArgs<Configuration>(emptyArray(), serviceDescription, log = log)
        log.info("Connecting to Zookeeper")
        val zk = runBlocking { ZooKeeperConnection(configuration.connConfig.zookeeper.servers).connect() }
        log.info("Connected to Zookeeper")

        val instance = serviceDescription.instance(configuration.connConfig)
        val node = runBlocking {
            log.info("Registering service...")
            zk.registerService(instance).also {
                log.debug("Service registered! Got back node: $it")
            }
        }

        install(DefaultHeaders)
        install(CallLogging)
        install(FreeMarker) {
            templateLoader = ClassTemplateLoader(Application::class.java.classLoader, "templates")
        }
        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule())
            }
        }

        install(Routing) {
            post("/api/auth-callback") {
                try {
                    val parameters = call.receiveParameters()
                    val access = parameters["accessToken"]!!
                    val refresh = parameters["refreshToken"]!!
                    call.respond(
                        FreeMarkerContent(
                            "Auth.ftl",
                            mapOf("accessToken" to access.escapeHTML(), "refreshToken" to refresh.escapeHTML())
                        )
                    )
                } catch (ex: Exception) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

        runBlocking { zk.markServiceAsReady(node, instance) }
        log.info("Server is ready!")
        log.info(instance.toString())
    }

    companion object {
        private val log = LoggerFactory.getLogger(Main::class.java)
    }
}

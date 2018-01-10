package esciencecloudui

import com.fasterxml.jackson.module.kotlin.KotlinModule
import freemarker.cache.ClassTemplateLoader
import io.ktor.features.*
import io.ktor.freemarker.FreeMarker
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.locations.Locations
import io.ktor.locations.location
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Routing
import io.ktor.util.generateCertificate
import io.ktor.util.hex
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import io.ktor.application.*
import io.ktor.content.files
import io.ktor.content.static
import io.ktor.jackson.jackson
import io.ktor.request.receiveParameters
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.sessions.*
import io.ktor.util.escapeHTML
import io.ktor.websocket.*
import webSockets
import java.io.File
import java.time.Duration

@location("/")
class Index

data class EScienceCloudUISession(val userId: String)

class EScienceCloudUIApp {

    val hashKey = hex("6819b57a326945c1968f45236589")
    val hmacKey = SecretKeySpec(hashKey, "HmacSHA1")

    fun hash(password: String): String {
        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(hmacKey)
        return hex(hmac.doFinal(password.toByteArray(Charsets.UTF_8)))
    }

    fun Application.module() {
        install(DefaultHeaders)
        install(CallLogging)
        install(FreeMarker) {
            templateLoader = ClassTemplateLoader(Application::class.java.classLoader, "templates")
        }
        install(ConditionalHeaders)
        install(PartialContentSupport)
        install(Locations)
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(10)
        }
        install(ContentNegotiation) {
            jackson {
                registerModule(KotlinModule())
            }
        }
        install(Sessions) {
            cookie<EScienceCloudUISession>("SESSION") {
                transform(SessionTransportTransformerMessageAuthentication(hashKey))
            }
        }

        val hashFunction = { s: String -> hash(s) }
        install(Routing) {
            static("/") {
                files("resources/app/css")
                files("resources/app/fonts")
                files("resources/app/img")
                files("resources/app/js")
                files("resources/app/vendor")
                files("resources/app/server")
                files("resources/app")
            }

            login(hashFunction)

            route("/ws/") {
                webSockets()
            }

            route("/api/") {
                ajaxOperations()
            }


            post("/auth") {
                val parameters = call.receiveParameters()
                val access = parameters["accessToken"]!!
                val refresh = parameters["refreshToken"]!!
                call.respond(FreeMarkerContent("Auth.ftl", mapOf("accessToken" to access.escapeHTML(), "refreshToken" to refresh.escapeHTML())))
            }

        }
    }
}

fun getApp(applicationName: String, applicationVersion: String): ApplicationAbacus? =
        applications.first { it.info.name == applicationName && it.info.version == applicationVersion }

class CertificateGenerator {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val file = File("build/temporary.jks")

            if (!file.exists()) {
                file.parentFile.mkdirs()
                generateCertificate(file)
            }
        }
    }
}

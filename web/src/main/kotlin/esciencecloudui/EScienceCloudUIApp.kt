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
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.sessions.*
import io.ktor.util.escapeHTML
import io.ktor.websocket.*
import webSockets
import java.io.File
import java.time.Duration

@location("/")
class Index

@location("/login")
data class Login(val accountName: String = "", val accountPassword: String = "")

@location("/logout")
class Logout

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

            // Home
            get("/") {
                call.respondRedirect("dashboard")
            }

            route("/dashboard") {
                requireAuthentication()
                get {
                    call.renderDashboard(ModelAndTemplate("dashboard.ftl", mapOf("title" to "Dashboard")))
                }
            }

            route("/files") {
                requireAuthentication()

                get {
                    call.renderDashboard(ModelAndTemplate("files.ftl", mapOf("title" to "Files")))
                }
            }

            route("/applications") {
                requireAuthentication()
                get {
                    call.renderDashboard(ModelAndTemplate("applications.ftl", mapOf("title" to "Applications")))
                }
            }

            route("/workflows") {
                requireAuthentication()
                get {
                    call.renderDashboard(ModelAndTemplate("workflows.ftl", mapOf("title" to "Workflows")))
                }
            }

            route("/analyses") {
                requireAuthentication()
                get {
                    call.renderDashboard(ModelAndTemplate("analyses.ftl", mapOf("title" to "Analyses")))
                }
            }

            route("/runApp/{appName}/{appVersion}") {
                requireAuthentication()
                get {
                    val appName = call.parameters["appName"]!!
                    val appVersion = call.parameters["appVersion"]!!
                    /* FIXME Correct way of getting application information
                        call.getAbacusApplication(appName, appVersion)
                    */
                    val app = getApp(appName, appVersion)!!
                    // TODO Fix escaping of HTML for application names
                    call.renderDashboard(ModelAndTemplate("runapp.ftl", mapOf("title" to "Run application", "appName" to  app.info.name.escapeHTML(), "appVersion" to appVersion.escapeHTML())))
                }
            }

            route("/activity/") {
                requireAuthentication()
                get("/messages") {
                    call.renderDashboard(ModelAndTemplate("messages.ftl", mapOf("title" to "Messages")))
                }
                get("/notifications") {
                    call.renderDashboard(ModelAndTemplate("notifications.ftl", mapOf("title" to "Notifications")))
                }
            }

            route("/status/") {
                requireAuthentication()
                get {
                    call.renderDashboard(ModelAndTemplate("status.ftl", mapOf("title" to "Status")))
                }
            }

            route("/projects") {
                requireAuthentication()
                get {
                    call.renderDashboard(ModelAndTemplate("projects.ftl", mapOf("title" to "Projects")))
                }
            }
        }
    }

    // TODO Find a better solution than this
    data class ModelAndTemplate(val templateName: String, val model: Map<String, Any> = emptyMap())

    private suspend fun ApplicationCall.renderDashboard(modelAndTemplate: ModelAndTemplate) {
        val session = sessions.get<EScienceCloudUISession>() ?: return respondRedirect("/login")
        val user = veryStupidActiveSessions[session]!!
        val username = user.username
        val model = modelAndTemplate.model.toMutableMap()
        model.putAll(
                mapOf("options" to DashboardOptions.nodes, "name" to username)
        )

        respond(FreeMarkerContent(modelAndTemplate.templateName, model))
    }
}

fun getApp(applicationName: String, applicationVersion: String): ApplicationAbacus? =
        applications.first { it.info.name == applicationName && it.info.version == applicationVersion }

data class OptionNode(val name: String, var icon: String, var href: String? = null, var children: ArrayList<OptionNode>? = null)

object DashboardOptions {
    val nodes = arrayListOf(
            OptionNode("Dashboard", "nav-icon", "/dashboard"),
            OptionNode("Files", "nav-icon", "/files"),
            OptionNode("Projects", "", "/projects"),
            OptionNode("Apps", "nav-icon", children = arrayListOf(
                    (OptionNode("Applications", "", "/applications")),
                    (OptionNode("Workflows", "", "/workflows")),
                    (OptionNode("Analyses", "", "/analyses")))),
            OptionNode("Activity", "", children = arrayListOf(
                    // (OptionNode("Messages", "", "/activity/messages")),
                    (OptionNode("Notifications", "", "/activity/notifications")))))
}

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

package esciencecloudui

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.locations.get
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.util.nextNonce
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.asynchttp.addBasicAuth

fun Route.login(/*dao: DAOFacade,*/ hash: (String) -> String) {
    post("/login") {
        // FIXME NO DO IN PRODUCTION
        // FIXME NO DO IN PRODUCTION
        // FIXME NO DO IN PRODUCTION
        // FIXME NO DO IN PRODUCTION
        if (!call.isLoggedIn()) {
            val parameters = call.receiveParameters()
            val username = parameters["accountName"]!!
            val password = parameters["accountPassword"]!!
            val success = call.logIn(username, password)

            if (success) {
                call.respondRedirect("/dashboard")
            } else {
                call.respondRedirect("/login")
            }
        } else {
            call.respondRedirect("/dashboard")
        }
    }

    get("/login") {
        if (call.isLoggedIn())
            call.respondRedirect("/dashboard")
        else
            call.respond(FreeMarkerContent("/login.ftl", emptyMap<String, String>()))
    }

    get<Logout> {
        call.sessions.clear<EScienceCloudUISession>()
        call.respondRedirect("/login")
    }
}

data class VeryStupidIRodsUser(val username: String, val password: String)

val veryStupidActiveSessions = HashMap<EScienceCloudUISession, VeryStupidIRodsUser>()
private suspend fun ApplicationCall.logIn(username: String, password: String): Boolean {
    val code = HttpClient.post("http://cloud.sdu.dk:8080/api/temp-auth") {
        addBasicAuth(username, password)
    }.statusCode

    if (code in 200..299) {
        val session = EScienceCloudUISession(nextNonce())
        veryStupidActiveSessions[session] = VeryStupidIRodsUser(username, password)
        sessions.set(session)
        return true
    }
    return false
}

data class User(val userName: String, val userId: String)

fun Route.requireAuthentication() {
    intercept(ApplicationCallPipeline.Infrastructure) {
        var session = call.sessions.get<EScienceCloudUISession>()
        val user = veryStupidActiveSessions[session]
        if (user == null) {
            call.sessions.clear<EScienceCloudUISession>()
            session = null
        }

        if (session == null) {
            call.respondRedirect("/login")
            finish()
        }
    }
}

fun ApplicationCall.isLoggedIn(): Boolean {
    val session = sessions.get<EScienceCloudUISession>()
    return veryStupidActiveSessions[session] != null
}

val ApplicationCall.irodsUser: VeryStupidIRodsUser
    get() {
        val session = sessions.get<EScienceCloudUISession>() ?: throw IllegalStateException("No session")
        return veryStupidActiveSessions[session] ?: throw IllegalStateException("Session not active")
    }
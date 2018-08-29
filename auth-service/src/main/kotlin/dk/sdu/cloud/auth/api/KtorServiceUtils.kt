package dk.sdu.cloud.auth.api

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.TokenValidation
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.auth.api.KtorServiceUtilsKt")

val ApplicationRequest.bearer: String?
    get() {
        val header = header(HttpHeaders.Authorization) ?: return null
        if (!header.startsWith("Bearer ")) {
            log.debug("Invalid prefix in authorization header: $header")
            return null
        }
        return header.substringAfter("Bearer ")
    }

fun Route.protect(rolesAllowed: List<Role> = Role.values().toList()) {
    intercept(ApplicationCallPipeline.Infrastructure) { protect(rolesAllowed) }
}

// TODO We should not just copy & paste this
suspend fun RESTHandler<*, *, *>.protect(rolesAllowed: List<Role> = Role.values().toList()): Boolean {
    if (call.attributes.getOrNull(jwtKey) == null) {
        log.debug("Could not find JWT")
        call.respond(HttpStatusCode.Unauthorized)
        return false
    }

    val role = call.request.principalRole
    if (role !in rolesAllowed) {
        log.debug("Role is not allowed on route: $role")
        call.respond(HttpStatusCode.Unauthorized)
        return false
    }

    return true
}

suspend fun PipelineContext<Unit, ApplicationCall>.protect(rolesAllowed: List<Role> = Role.values().toList()): Boolean {
    if (call.attributes.getOrNull(jwtKey) == null) {
        log.debug("Could not find JWT")
        call.respond(HttpStatusCode.Unauthorized)
        finish()
        return false
    }

    val role = call.request.principalRole
    if (role !in rolesAllowed) {
        log.debug("Role is not allowed on route: $role")
        call.respond(HttpStatusCode.Unauthorized)
        finish()
        return false
    }

    return true
}

class JWTProtection {
    suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>): Unit = with(context) {
        log.debug("Intercepting call for protect()")
        val token = call.request.bearer ?: return run {
            log.debug("Did not find a bearer token")
        }

        // We call finish if the last two fails as they would indicate actual failure
        // (and not just missing auth, which can be okay)
        val validated = TokenValidation.validateOrNull(token) ?: run {
            log.debug("The following token did not pass validation: $token")
            call.respond(HttpStatusCode.Unauthorized)
            finish()
            return
        }

        val roleAsString = validated.getClaim("role").asString()
        val role = try {
            Role.valueOf(roleAsString)
        } catch (ex: Exception) {
            when (ex) {
                is NoSuchElementException, is IllegalArgumentException -> {
                    log.warn("Unknown role attribute in validated token! Role: $roleAsString")
                    call.respond(HttpStatusCode.InternalServerError)
                    finish()
                    return
                }

                else -> throw ex
            }

        }

        call.request.validatedPrincipal = validated
        call.request.principalRole = role
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Unit, JWTProtection> {
        override val key = AttributeKey<JWTProtection>("jwtProtection")

        override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit): JWTProtection {
            val feature = JWTProtection()
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(this) }
            return feature
        }
    }
}


private val jwtKey = AttributeKey<SecurityPrincipal>("JWT")
var ApplicationRequest.validatedPrincipal: SecurityPrincipal
    get() = call.attributes[jwtKey]
    private set(value) = call.attributes.put(jwtKey, value)

private val roleKey = AttributeKey<Role>("role")
var ApplicationRequest.principalRole: Role
    get() = call.attributes[roleKey]
    private set(value) = call.attributes.put(roleKey, value)

fun Role.isPrivileged(): Boolean = this in PRIVILEGED_ROLES
val PRIVILEGED_ROLES = listOf(Role.SERVICE, Role.ADMIN)

typealias SecurityPrincipal = DecodedJWT

val ApplicationRequest.currentUsername: String
    get() = validatedPrincipal.subject


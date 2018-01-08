package dk.sdu.cloud.auth.api

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.service.TokenValidation
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.auth.api.KtorServiceUtilsKt")

private val ApplicationRequest.bearer: String?
    get() {
        val header = header(HttpHeaders.Authorization) ?: return null
        if (!header.startsWith("Bearer ")) {
            log.debug("Invalid prefix in authorization header: $header")
            return null
        }
        return header.substringAfter("Bearer ")
    }

fun Route.protect(vararg rolesAllowed: Role) = protect(rolesAllowed.toList())

fun Route.protect(rolesAllowed: List<Role> = Role.values().toList()) {
    intercept(ApplicationCallPipeline.Infrastructure) {
        if (call.attributes.getOrNull(jwtKey) != null) {
            throw IllegalStateException("protect() should only be called once per route")
        }

        val token = call.request.bearer ?: run {
            log.debug("Did not receive a bearer token in the header of the request!")
            call.respond(HttpStatusCode.Unauthorized)
            finish()
            return@intercept
        }

        val validated = TokenValidation.validateOrNull(token) ?: run {
            log.debug("The following token did not pass validation: $token")
            call.respond(HttpStatusCode.Unauthorized)
            finish()
            return@intercept
        }

        val roleAsString = validated.getClaim("role").asString()
        val role = try {
            Role.valueOf(roleAsString)
        } catch (ex: NoSuchElementException) {
            log.warn("Unknown role attribute in validated token! Role: $roleAsString")
            call.respond(HttpStatusCode.InternalServerError)
            finish()
            return@intercept
        }

        if (role !in rolesAllowed) {
            log.debug("Role is not allowed on route: $role")
            call.respond(HttpStatusCode.Unauthorized)
            finish()
            return@intercept
        }

        call.request.validatedPrincipal = validated
    }
}

private val jwtKey = AttributeKey<DecodedJWT>("JWT")
var ApplicationRequest.validatedPrincipal: DecodedJWT
    get() = call.attributes[jwtKey]
    private set(value) = call.attributes.put(jwtKey, value)


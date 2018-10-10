package dk.sdu.cloud.auth.api

import dk.sdu.cloud.Role
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.nullableSecurityToken
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationRequest
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.util.AttributeKey

@Deprecated(message = "Built into service-common", replaceWith = ReplaceWith(""))
@Suppress("deprecation")
fun Route.protect(rolesAllowed: List<Role> = Role.values().toList()) {
    intercept(ApplicationCallPipeline.Infrastructure) { protect(rolesAllowed) }
}

@Deprecated(message = "Built into service-common")
suspend fun RESTHandler<*, *, *, *>.protect(
    rolesAllowed: List<dk.sdu.cloud.Role> = dk.sdu.cloud.Role.values().toList()
): Boolean {
    if (call.nullableSecurityToken?.principal?.role !in rolesAllowed) {
        call.respond(HttpStatusCode.Unauthorized)
        return false
    }

    return true
}

@Deprecated(message = "Built into service-common")
suspend fun PipelineContext<Unit, ApplicationCall>.protect(rolesAllowed: List<Role> = Role.values().toList()): Boolean {
    if (call.nullableSecurityToken?.principal?.role !in rolesAllowed) {
        call.respond(HttpStatusCode.Unauthorized)
        finish()
        return false
    }
    return true
}

@Deprecated(message = "Built into implement call automatically")
@Suppress("deprecation")
class JWTProtection {
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Unit, JWTProtection> {
        override val key = AttributeKey<JWTProtection>("jwtProtection")

        override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit): JWTProtection {
            return JWTProtection()
        }
    }
}

@Deprecated(message = "Built into service-common", replaceWith = ReplaceWith("call.securityPrincipal"))
@Suppress("deprecation")
val ApplicationRequest.validatedPrincipal: SecurityPrincipal
    get() = call.securityPrincipal

@Deprecated(message = "Built into service-common", replaceWith = ReplaceWith("call.securityPrincipal.role"))
val ApplicationRequest.principalRole: dk.sdu.cloud.Role
    get() = call.securityPrincipal.role

@Deprecated(message = "Built into client-core", replaceWith = ReplaceWith("this in Roles.PRIVILEGED"))
@Suppress("deprecation")
fun Role.isPrivileged(): Boolean = this in PRIVILEGED_ROLES

@Deprecated(message = "Built into client-core")
val PRIVILEGED_ROLES = listOf(Role.SERVICE, Role.ADMIN)

@Deprecated(message = "Built into service-common")
typealias SecurityPrincipal = dk.sdu.cloud.SecurityPrincipal

@Deprecated(message = "Built into service-common", replaceWith = ReplaceWith("call.securityPrincipal.username"))
val ApplicationRequest.currentUsername: String
    get() = call.securityPrincipal.username


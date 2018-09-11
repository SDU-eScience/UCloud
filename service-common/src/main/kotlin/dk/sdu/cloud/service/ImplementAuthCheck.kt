package dk.sdu.cloud.service

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.client.RESTCallDescription
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext

class ImplementAuthCheck {
    private lateinit var description: RESTCallDescription<*, *, *, *>
    fun <A : Any, B : Any, C : Any, D : Any> call(call: RESTCallDescription<A, B, C, D>) {
        this.description = call
    }

    private fun validateConfiguration() {
        if (!this::description.isInitialized) {
            throw IllegalStateException("Must configure call() description")
        }
    }

    private suspend fun interceptBefore(ctx: PipelineContext<*, ApplicationCall>) {
        val call = ctx.context
        val tokenMustValidate = Role.GUEST !in description.auth.roles

        val bearer = call.request.bearer

        if (bearer == null && tokenMustValidate) {
            log.debug("Missing bearer token (required)")
            call.respond(HttpStatusCode.Unauthorized)
            ctx.finish()
            return
        }

        if (bearer != null) {
            val validatedToken = TokenValidation.validateOrNull(bearer)

            if (validatedToken == null && tokenMustValidate) {
                log.debug("Invalid bearer token (required)")
                call.respond(HttpStatusCode.Unauthorized)
                ctx.finish()
                return
            } else if (validatedToken != null) {
                val firstNames = validatedToken.requiredClaim(ctx, "firstNames") { it.asString() } ?: return
                val lastName = validatedToken.requiredClaim(ctx, "lastName") { it.asString() } ?: return
                val role = validatedToken.requiredClaim(ctx, "role") { Role.valueOf(it.asString()) } ?: return

                val principal = SecurityPrincipal(
                    validatedToken.subject,
                    role,
                    firstNames,
                    lastName
                )

                val issuedAt = validatedToken.issuedAt.time
                val expiresAt = validatedToken.expiresAt.time

                val scopes = try {
                    validatedToken.audience.map { SecurityScope.parseFromString(it) }
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    call.respond(HttpStatusCode.InternalServerError)
                    ctx.finish()
                    return
                }

                val token = SecurityPrincipalToken(
                    principal,
                    "", // TODO
                    scopes,
                    issuedAt,
                    expiresAt
                )

                call.securityToken = token

                if (call.securityPrincipal.role !in description.auth.roles) {
                    log.debug("Security principal is not authorized for this call")
                    call.respond(HttpStatusCode.Unauthorized)
                    ctx.finish()
                    return
                }

                val requiredScope = description.requiredAuthScope
                val isCovered = scopes.any { requiredScope.isCoveredBy(it) }

                if (!isCovered) {
                    log.debug("Missing scope: $requiredScope in $scopes")
                    call.respond(HttpStatusCode.Unauthorized)
                    ctx.finish()
                    return
                }
            }
        }
    }

    private suspend fun <T> DecodedJWT.requiredClaim(
        ctx: PipelineContext<*, ApplicationCall>,
        name: String,
        mapper: (Claim) -> T
    ): T? {
        val call = ctx.context
        val claim = getClaim(name)
        if (claim == null) {
            log.warn("Could not find claim '$name'")
            call.respond(HttpStatusCode.InternalServerError)
            ctx.finish()
            return null
        }

        return try {
            mapper(claim)!!
        } catch (ex: Exception) {
            log.warn("Could not transform claim '$name'")
            log.warn(ex.stackTraceToString())
            ctx.finish()
            null
        }
    }

    companion object Feature :
        ApplicationFeature<ApplicationCallPipeline, ImplementAuthCheck, ImplementAuthCheck>,
        Loggable {
        override val key: AttributeKey<ImplementAuthCheck> = AttributeKey("implement-auth-check")
        override val log = logger()

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: ImplementAuthCheck.() -> Unit
        ): ImplementAuthCheck {
            val feature = ImplementAuthCheck()
            feature.configure()
            feature.validateConfiguration()

            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.interceptBefore(this) }
            return feature
        }
    }
}

val ApplicationRequest.bearer: String?
    get() {
        val header = header(HttpHeaders.Authorization) ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substringAfter("Bearer ")
    }

private val securityTokenKey = AttributeKey<SecurityPrincipalToken>("security-principal-token")

val ApplicationCall.nullableSecurityToken: SecurityPrincipalToken?
    get() = attributes.getOrNull(securityTokenKey)

var ApplicationCall.securityToken: SecurityPrincipalToken
    get() = attributes[securityTokenKey]

    internal set(value) {
        attributes.put(securityTokenKey, value)
    }

val ApplicationCall.securityPrincipal: SecurityPrincipal
    get() = securityToken.principal

suspend fun RESTHandler<*, *, *, *>.protect(
    rolesAllowed: List<dk.sdu.cloud.Role> = dk.sdu.cloud.Role.values().toList()
): Boolean {
    if (call.nullableSecurityToken?.principal?.role !in rolesAllowed) {
        call.respond(HttpStatusCode.Unauthorized)
        return false
    }

    return true
}

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
                try {
                    val token = validatedToken.toSecurityToken()
                    call.securityToken = token

                    if (call.securityPrincipal.role !in description.auth.roles) {
                        log.debug("Security principal is not authorized for this call")
                        log.debug("Principal is: ${call.securityPrincipal}")
                        call.respond(HttpStatusCode.Unauthorized)
                        ctx.finish()
                        return
                    }

                    token.requireScope(description.requiredAuthScope)
                } catch (ex: RPCException) {
                    log.debug(ex.stackTraceToString())
                    call.respond(ex.httpStatusCode, ex.why)
                    ctx.finish()
                    return
                }
            }
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

sealed class JWTException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class InternalError(why: String) : JWTException(why, HttpStatusCode.InternalServerError)
    class MissingScope : JWTException("Missing scope", HttpStatusCode.Unauthorized)
}

private fun <T> DecodedJWT.requiredClaim(
    name: String,
    mapper: (Claim) -> T
): T {
    val claim = getClaim(name) ?: throw JWTException.InternalError("Could not find claim '$name'")

    return try {
        mapper(claim)!!
    } catch (ex: Exception) {
        throw JWTException.InternalError("Could not transform claim '$name'")
    }
}

fun DecodedJWT.toSecurityToken(): SecurityPrincipalToken {
    val validatedToken = this
    val firstNames = validatedToken.requiredClaim("firstNames") { it.asString() }
    val lastName = validatedToken.requiredClaim("lastName") { it.asString() }
    val role = validatedToken.requiredClaim("role") { Role.valueOf(it.asString()) }

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
        throw JWTException.InternalError(ex.stackTraceToString())
    }

    return SecurityPrincipalToken(
        principal,
        "", // TODO
        scopes,
        issuedAt,
        expiresAt
    )
}

fun SecurityPrincipalToken.requireScope(requiredScope: SecurityScope) {
    val isCovered = scopes.any { requiredScope.isCoveredBy(it) }
    if (!isCovered) throw JWTException.MissingScope()
}
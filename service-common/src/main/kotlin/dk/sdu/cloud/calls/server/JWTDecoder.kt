package dk.sdu.cloud.calls.server

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import org.slf4j.LoggerFactory

@Deprecated("Use ctx.bearer instead")
val ApplicationRequest.bearer: String?
    get() {
        val header = header(HttpHeaders.Authorization) ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substringAfter("Bearer ")
    }

sealed class JWTException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class InternalError(why: String) : JWTException(why, HttpStatusCode.InternalServerError)
    class MissingScope : JWTException("Missing scope", HttpStatusCode.Unauthorized)
}

private fun <T> DecodedJWT.optionalClaim(
    name: String,
    mapper: (Claim) -> T
): T? {
    return runCatching { requiredClaim(name, mapper) }.getOrNull()
}

private fun <T> DecodedJWT.requiredClaim(
    name: String,
    mapper: (Claim) -> T
): T {
    val claim = getClaim(name) ?: throw JWTException.InternalError("Could not find claim '$name'")

    @Suppress("TooGenericExceptionCaught")
    return try {
        mapper(claim)!!
    } catch (ex: Exception) {
        throw JWTException.InternalError("Could not transform claim '$name'")
    }
}

fun DecodedJWT.toSecurityToken(): SecurityPrincipalToken {
    val validatedToken = this
    val role = validatedToken.optionalClaim("role") { Role.valueOf(it.asString()) } ?: Role.UNKNOWN
    val firstNames = validatedToken.optionalClaim("firstNames") { it.asString() } ?: subject
    val lastName = validatedToken.optionalClaim("lastName") { it.asString() } ?: subject

    val publicSessionReference = validatedToken
        .getClaim("publicSessionReference")
        .takeIf { !it.isNull }
        ?.asString()

    val extendedBy = validatedToken
        .getClaim("extendedBy")
        .takeIf { !it.isNull }
        ?.asString()

    val principal = SecurityPrincipal(
        validatedToken.subject,
        role,
        firstNames,
        lastName,
        validatedToken.getClaim("uid").asLong()
    )

    val issuedAt = validatedToken.issuedAt.time
    val expiresAt = validatedToken.expiresAt.time

    @Suppress("TooGenericExceptionCaught")
    val scopes =
        validatedToken.audience.mapNotNull {
            try {
                SecurityScope.parseFromString(it)
            } catch (ex: Exception) {
                authCheckLog.info(ex.stackTraceToString())
                null
            }
        }

    val extendedByChain = validatedToken
        .getClaim("extendedByChain")
        .takeIf { !it.isNull }
        ?.asList(String::class.java) ?: emptyList()

    val backwardsCompatibleChain =
        if (extendedByChain.isEmpty() && extendedBy != null) listOf(extendedBy) else extendedByChain

    return SecurityPrincipalToken(
        principal,
        scopes,
        issuedAt,
        expiresAt,
        publicSessionReference,
        extendedBy,
        backwardsCompatibleChain
    )
}

private val authCheckLog = LoggerFactory.getLogger("dk.sdu.cloud.service.ImplementAuthCheckKt")

fun SecurityPrincipalToken.requireScope(requiredScope: SecurityScope) {
    val isCovered = scopes.any { requiredScope.isCoveredBy(it) }
    if (!isCovered) throw JWTException.MissingScope()
}

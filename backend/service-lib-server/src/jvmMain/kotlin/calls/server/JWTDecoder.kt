package dk.sdu.cloud.calls.server

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.service.JWTException
import io.ktor.http.HttpHeaders
import io.ktor.request.ApplicationRequest
import io.ktor.request.header

@Deprecated("Use ctx.bearer instead")
val ApplicationRequest.bearer: String?
    get() {
        val header = header(HttpHeaders.Authorization) ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substringAfter("Bearer ")
    }

fun SecurityPrincipalToken.requireScope(requiredScope: SecurityScope) {
    val isCovered = scopes.any { requiredScope.isCoveredBy(it) }
    if (!isCovered) throw JWTException.MissingScope()
}

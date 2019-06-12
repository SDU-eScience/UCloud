package dk.sdu.cloud.app.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode

fun SecurityPrincipalToken.realUsername(): String {
    if (principal.role != Role.PROJECT_PROXY) {
        return principal.username
    }

    return extendedByChain.lastOrNull() ?: throw RPCException(
        "Project token not extended by real user",
        HttpStatusCode.InternalServerError
    )
}

fun SecurityPrincipalToken.projectOrNull(): String? {
    if (principal.role != Role.PROJECT_PROXY) {
        return null
    }

    return principal.username.substringBeforeLast('#')
}

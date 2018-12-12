package dk.sdu.cloud.auth.services

import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode

sealed class ExtensionException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class BadRequest(why: String) : ExtensionException(
        why,
        HttpStatusCode.BadRequest
    )

    class Unauthorized(why: String) : ExtensionException(
        why,
        HttpStatusCode.Unauthorized
    )

    class InternalError(why: String) : ExtensionException(
        why,
        HttpStatusCode.InternalServerError
    )
}

package dk.sdu.cloud.auth.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException

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

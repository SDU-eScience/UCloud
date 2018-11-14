package dk.sdu.cloud.share.services

import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode

sealed class ShareException(override val message: String, statusCode: HttpStatusCode) :
    RPCException(message, statusCode) {
    class NotFound : ShareException("Not found",
        HttpStatusCode.NotFound
    )
    class NotAllowed : ShareException("Not allowed",
        HttpStatusCode.Forbidden
    )
    class DuplicateException : ShareException("Already exists",
        HttpStatusCode.Conflict
    )
    class PermissionException : ShareException("Not allowed",
        HttpStatusCode.Forbidden
    )
    class BadRequest(why: String) : ShareException("Bad request: $why",
        HttpStatusCode.BadRequest
    )
    class InternalError(why: String) : ShareException("Internal error: $why",
        HttpStatusCode.InternalServerError
    )
}

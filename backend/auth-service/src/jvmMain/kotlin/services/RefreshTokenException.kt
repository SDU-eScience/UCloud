package dk.sdu.cloud.auth.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException

sealed class RefreshTokenException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class InvalidToken : RefreshTokenException("Invalid token", HttpStatusCode.Unauthorized)
    class InternalError : RefreshTokenException("Internal server error", HttpStatusCode.InternalServerError)
}

package dk.sdu.cloud.auth.services

import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode

sealed class RefreshTokenException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class InvalidToken : RefreshTokenException("Invalid token", HttpStatusCode.Unauthorized)
    class InternalError : RefreshTokenException("Internal server error", HttpStatusCode.InternalServerError)
}

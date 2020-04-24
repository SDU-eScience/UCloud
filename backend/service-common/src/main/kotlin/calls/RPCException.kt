package dk.sdu.cloud.calls

import io.ktor.http.HttpStatusCode

open class RPCException(val why: String, val httpStatusCode: HttpStatusCode) : RuntimeException(why) {
    companion object {
        fun fromStatusCode(httpStatusCode: HttpStatusCode, message: String? = null) =
            RPCException(message ?: httpStatusCode.description, httpStatusCode)
    }
}

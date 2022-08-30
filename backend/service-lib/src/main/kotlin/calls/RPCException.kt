package dk.sdu.cloud.calls

open class RPCException(val why: String, val httpStatusCode: HttpStatusCode, val errorCode: String? = null) :
    RuntimeException(why) {
    companion object {
        fun fromStatusCode(httpStatusCode: HttpStatusCode, message: String? = null, errorCode: String? = null) =
            RPCException(message ?: httpStatusCode.description, httpStatusCode, errorCode)
    }
}

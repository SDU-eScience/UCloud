package dk.sdu.cloud.app.services

import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode

sealed class JobException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound(entity: String) : JobException(
        "Not found: $entity",
        HttpStatusCode.NotFound
    )

    class InvalidRequest(why: String = "") : JobException("Bad request: $why", HttpStatusCode.BadRequest)

    class VerificationError(why: String = "") : JobException("Job verification failed. $why", HttpStatusCode.BadRequest)
}
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

    class TransferError(why: String = "") :
        JobException("Could not transfer files to computation. $why", HttpStatusCode.BadRequest)

    class BadStateTransition(why: String = "") : JobException("Bad state transition", HttpStatusCode.BadRequest)
}

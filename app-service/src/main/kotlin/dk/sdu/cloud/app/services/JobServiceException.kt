package dk.sdu.cloud.app.services

import io.ktor.http.HttpStatusCode

sealed class JobServiceException(message: String, val statusCode: HttpStatusCode) : RuntimeException(message) {
    data class NotFound(val entity: String) : JobServiceException("Not found: $entity",
        HttpStatusCode.NotFound
    )
    class NotReady : JobServiceException("Not ready yet",
        HttpStatusCode.BadRequest
    )
    class AlreadyComplete : JobServiceException("Job already complete",
        HttpStatusCode.BadRequest
    )
    class InvalidRequest(why: String) : JobServiceException("Bad request. $why",
        HttpStatusCode.BadRequest
    )
}
package dk.sdu.cloud.accounting.services.projects

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException

sealed class ProjectException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    class NotFound : ProjectException(
        "Not found",
        HttpStatusCode.NotFound
    )

    class Forbidden : ProjectException(
        "Permission denied",
        HttpStatusCode.Forbidden
    )
}

package dk.sdu.cloud.service

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import io.ktor.http.HttpStatusCode

fun <T> RESTResponse<T, *>.orThrow(): T {
    if (this !is RESTResponse.Ok) {
        throw RPCException(rawResponseBody, HttpStatusCode.fromValue(status))
    }
    return result
}

fun AuthenticatedCloud.optionallyCausedBy(causedBy: String?): AuthenticatedCloud {
    return if (causedBy != null) withCausedBy(causedBy)
    else this
}
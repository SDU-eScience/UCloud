package dk.sdu.cloud.service

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import java.io.InputStream

data class RESTResponseContent(
    val stream: InputStream,
    val contentLength: Long?,
    val contentType: ContentType?
)

val RESTResponse<*, *>.okContent: RESTResponseContent
    get() {
        orThrow()

        val ok = this as RESTResponse.Ok
        val contentLength = ok.response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val contentType = try {
            ok.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
        } catch (ex: BadContentTypeFormatException) {
            null
        }

        val stream = ok.response.content.toInputStream()
        return RESTResponseContent(stream, contentLength, contentType)
    }

val RESTResponse<*, *>.okContentOrNull: RESTResponseContent?
    get() {
        return try {
            okContent
        } catch (ex: RPCException) {
            null
        }
    }

fun <T> RESTResponse<T, *>.orThrow(): T {
    if (this !is RESTResponse.Ok) {
        throw RPCException(rawResponseBody, HttpStatusCode.fromValue(status))
    }
    return result
}

fun <T> RESTResponse<T, *>.orNull(): T? {
    if (this !is RESTResponse.Ok) {
        return null
    }
    return result
}

fun AuthenticatedCloud.optionallyCausedBy(causedBy: String?): AuthenticatedCloud {
    return if (causedBy != null) withCausedBy(causedBy)
    else this
}

package dk.sdu.cloud.service

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import java.io.InputStream

@Deprecated("use RESTResponseChannel instead")
data class RESTResponseContent(
    val stream: InputStream,
    val contentLength: Long?,
    val contentType: ContentType?
)

data class RESTResponseChannel(
    val stream: ByteReadChannel,
    val contentLength: Long?,
    val contentType: ContentType?
)

@Deprecated("use okChannel instead", ReplaceWith("okChannel"))
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

@Deprecated("use okChannelOrNull instead", ReplaceWith("okChannelOrNull"))
val RESTResponse<*, *>.okContentOrNull: RESTResponseContent?
    get() {
        return try {
            okContent
        } catch (ex: RPCException) {
            null
        }
    }

val RESTResponse<*, *>.okChannel: RESTResponseChannel
    get() {
        orThrow()

        val ok = this as RESTResponse.Ok
        val contentLength = ok.response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val contentType = try {
            ok.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
        } catch (ex: BadContentTypeFormatException) {
            null
        }

        val stream = ok.response.content
        return RESTResponseChannel(stream, contentLength, contentType)
    }

val RESTResponse<*, *>.okChannelOrNull: RESTResponseChannel?
    get() {
        return try {
            okChannel
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

fun <T, E> RESTResponse<T, E>.orRethrowAs(rethrow: (RESTResponse.Err<T, E>) -> Nothing): T {
    when (this) {
        is RESTResponse.Ok -> {
            return result
        }

        is RESTResponse.Err -> {
            rethrow(this)
        }
    }
}

fun <T, E> RESTResponse<T, E>.throwIfInternal(): RESTResponse<T, E> {
    if (status in 500..599) throw RPCException(rawResponseBody, HttpStatusCode.fromValue(status))
    return this
}

fun <T, E> RESTResponse<T, E>.throwIfInternalOrBadRequest(): RESTResponse<T, E> {
    if (status in 500..599 || status == 400) throw RPCException(rawResponseBody, HttpStatusCode.InternalServerError)
    return this
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

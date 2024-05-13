package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.fileName
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.copyTo
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*

class DownloadService(
    private val pathConverter: PathConverter,
    private val fs: NativeFS,
) {
    suspend fun download(file: UCloudFile, ctx: HttpCall) {
        if (pathConverter.pathIsSensitive(file)) {
            throw RPCException("Downloads are disabled for this project.", dk.sdu.cloud.calls.HttpStatusCode.Forbidden)
        }

        val internalFile = pathConverter.ucloudToInternal(file)

        val stat = fs.stat(internalFile)
        if (stat.fileType != FileType.FILE) {
            throw RPCException(
                "UCloud/Storage does not support download of anything but normal files. " +
                    "Try zipping your folders if needed.",
                dk.sdu.cloud.calls.HttpStatusCode.BadRequest
            )
        }


        // TODO Ban the downloads of sensitive files
        val contentType = ContentType.defaultForFilePath(internalFile.path)
        ctx.ktor.call.response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"${internalFile.path.safeFileName()}\""
        )

        val size = stat.size
        val statusCode = HttpStatusCode.OK

        ctx.ktor.call.respond(
            DirectWriteContent(
                contentLength = size,
                contentType = contentType,
                status = statusCode
            ) {
                val writeChannel = this
                fs.openForReading(internalFile).use { it.copyTo(writeChannel) }
            }
        )
    }

    companion object : Loggable {
        override val log = logger()
        private val safeFileNameChars =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-+,@£$€!½§~'=()[]{}0123456789".let {
                CharArray(it.length) { i -> it[i] }.toSet()
            }

        private fun String.safeFileName(): String {
            val normalName = fileName()
            return buildString(normalName.length) {
                normalName.forEach {
                    when (it) {
                        in safeFileNameChars -> append(it)
                        else -> append('_')
                    }
                }
            }
        }
    }
}

class DirectWriteContent(
    override val contentLength: Long? = null,
    override val contentType: ContentType? = null,
    override val status: HttpStatusCode? = null,
    private val writer: suspend ByteWriteChannel.() -> Unit
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        writer(channel)
    }
}

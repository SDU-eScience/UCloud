package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.FilesCreateDownloadResponseItem
import dk.sdu.cloud.file.orchestrator.api.FilesProviderCreateDownloadRequestItem
import dk.sdu.cloud.file.orchestrator.api.fileName
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.parameterList
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.util.*

class DownloadService(
    private val providerId: String,
    private val db: DBContext,
    private val pathConverter: PathConverter,
    private val fs: NativeFS,
) {
    suspend fun createSessions(
        request: BulkRequest<FilesProviderCreateDownloadRequestItem>
    ): BulkResponse<FilesCreateDownloadResponseItem?> {
        val responses = ArrayList<FilesCreateDownloadResponseItem>()
        for (reqItem in request.items) {
            responses.add(FilesCreateDownloadResponseItem(endpoint + UUID.randomUUID().toString()))
        }

        val relativePaths = ArrayList<String>()
        for (reqItem in request.items) {
            val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(reqItem.id))
            val relativeFile = pathConverter.internalToRelative(internalFile)
            fs.stat(internalFile)
            relativePaths.add(relativeFile.path)
        }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    val ids by parameterList<String>()
                    for (resp in responses) {
                        ids.add(resp.endpoint.removePrefix(endpoint))
                    }

                    setParameter("relative_paths", relativePaths)
                },
                """
                    insert into file_ucloud.download_sessions(id, relative_path)
                    select unnest(:ids::text[]) id, unnest(:relative_paths::text[]) relative_path
                """
            )
        }
        return BulkResponse(responses)
    }

    suspend fun download(token: String, ctx: HttpCall) {
        val relativeFile = RelativeInternalFile(db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("token", token) },
                """
                    select relative_path
                    from file_ucloud.download_sessions
                    where
                        id = :token and
                        now() - last_update < '24 hours'::interval
                """
            ).rows.map { it.getString(0)!! }.singleOrNull()
                ?: throw RPCException("Invalid download link. Try again later.", dk.sdu.cloud.calls.HttpStatusCode.NotFound)
        })

        val internalFile = pathConverter.relativeToInternal(relativeFile)

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
        ctx.call.response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"${internalFile.path.safeFileName()}\""
        )

        // NOTE(Dan): We support a single byte range. Any other unit or multiple ranges will cause us to
        // ignore the request and just send the full document.
        val range: LongRange? = run {
            val range = ctx.call.request.ranges() ?: return@run null
            if (range.unit != "bytes") return@run null
            if (range.ranges.size != 1) return@run null

            val result = when (val actualRange = range.ranges.single()) {
                is ContentRange.Bounded -> (actualRange.from)..(actualRange.to)
                is ContentRange.TailFrom -> (actualRange.from) until (stat.size)
                is ContentRange.Suffix -> (stat.size - actualRange.lastCount) until (stat.size)
                else -> error("Unknown range size")
            }

            if (result.first >= result.last) return@run null
            return@run result
        }

        val size = if (range == null) {
            stat.size
        } else {
            if (range.first == 0L) {
                range.last - range.first + 1
            } else {
                range.last - range.first
            }
        }

        val statusCode = if (range == null) HttpStatusCode.OK else HttpStatusCode.PartialContent

        if (range != null) {
            ctx.call.response.header(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/$size")
        }

        ctx.call.respond(
            DirectWriteContent(
                contentLength = size,
                contentType = contentType,
                status = statusCode
            ) {
                val writeChannel = this
                fs.openForReading(internalFile).use { it.copyTo(writeChannel.toOutputStream()) }
            }
        )
    }

    private val endpointWithoutToken = "/ucloud/$providerId/download"
    private val endpoint = "$endpointWithoutToken?token="

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

package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import kotlin.math.max

class ChunkedUploadService(
    private val db: DBContext,
    private val pathConverter: PathConverter,
    private val nativeFS: NativeFS,
) {
    suspend fun createSession(
        file: UCloudFile,
        conflictPolicy: WriteConflictPolicy,
    ): String {
        try {
            val internalFile = pathConverter.ucloudToInternal(file)
            val relativeFile = pathConverter.internalToRelative(internalFile)
            val id = UUID.randomUUID().toString()

            val (fileName, outs) = nativeFS.openForWriting(internalFile, conflictPolicy)
            @Suppress("BlockingMethodInNonBlockingContext")
            outs.close()

            val destination = relativeFile.parent().path.removeSuffix("/") + "/" + fileName

            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("destination", destination)
                    },
                    """
                    insert into file_ucloud.upload_sessions (id, relative_path, last_update) values
                    (:id, :destination, now())
                """
                )
            }
            return id
        } catch (ex: Throwable) {
            ex.printStackTrace()
            throw ex
        }
    }

    suspend fun receiveChunk(
        request: ChunkedUploadProtocolUploadChunkRequest,
        contentLength: Long,
        payload: ByteReadChannel,
    ) {
        if (contentLength !in 0..ChunkedUploadProtocol.MAX_CHUNK_SIZE) {
            throw RPCException("Invalid chunk size specified: $contentLength", HttpStatusCode.BadRequest)
        }

        val relativeFile = db.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("id", request.token) },
                    """
                        update file_ucloud.upload_sessions
                        set last_update = now()
                        where 
                            id = :id and
                            last_update >= now() - interval '48 hours'
                        returning relative_path
                    """
                )
                .rows
                .map { it.getString("relative_path")!! }
                .singleOrNull()
                ?.let { RelativeInternalFile(it) }
        } ?: throw FSException.BadRequest()

        val internalFile = pathConverter.relativeToInternal(relativeFile)

        val (_, outs) = nativeFS.openForWriting(
            internalFile,
            WriteConflictPolicy.REPLACE,
            truncate = false,
            offset = request.offset.takeIf { it != -1L }
        )

        outs.use {
            // NOTE(Dan): We require at least 20KB/s transfer from clients to avoid denial-of-service attacks
            withTimeoutOrNull(max(10_000, contentLength / 20_000 * 1000)) {
                payload.copyTo(outs, limit = contentLength)
            }
        }

        db.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("id", request.token) },
                    "update file_ucloud.upload_sessions set last_update = now() where id = :id"
                )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

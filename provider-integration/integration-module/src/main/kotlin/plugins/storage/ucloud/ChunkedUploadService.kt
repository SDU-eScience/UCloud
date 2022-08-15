package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.RelativeInternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.parent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
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
                session.prepareStatement(
                    """
                        insert into ucloud_storage_upload_sessions (id, relative_path, last_update) values
                        (:id, :destination, now())
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", id)
                        bindString("destination", destination)
                    }
                )
            }
            return id
        } catch (ex: Throwable) {
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
            val rows = ArrayList<String>()
            session
                .prepareStatement(
                    """
                        update ucloud_storage_upload_sessions
                        set last_update = now()
                        where 
                            id = :id and
                            last_update >= now() - interval '48 hours'
                        returning relative_path
                    """
                )
                .useAndInvoke(
                    prepare = { bindString("id", request.token) },
                    readRow = { row -> rows.add(row.getString(0)!!) }
                )

            rows
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
                .prepareStatement("update ucloud_storage_upload_sessions set last_update = now() where id = :id")
                .useAndInvokeAndDiscard(
                    prepare = {
                        bindString("id", request.token)
                    }
                )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

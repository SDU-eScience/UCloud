package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.copyTo
import io.ktor.utils.io.*

class ChunkedUploadService(
    private val pathConverter: PathConverter,
    private val nativeFS: NativeFS,
) {
    suspend fun receiveChunk(
        path: UCloudFile,
        offset: Long,
        payload: ByteReadChannel,
    ) {
        val internalFile = pathConverter.ucloudToInternal(path)

        val (_, outs) = nativeFS.openForWriting(
            internalFile,
            WriteConflictPolicy.REPLACE,
            truncate = false,
            offset = offset
        )

        outs.use {
            payload.copyTo(outs)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

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
        totalSize: Long,
        payload: ByteReadChannel,
        conflictPolicy: WriteConflictPolicy
    ) {
        val tmpInternalFile = if (conflictPolicy == WriteConflictPolicy.REPLACE) {
            pathConverter.ucloudToInternal(UCloudFile.create(path.path + ".part"))
        } else {
            pathConverter.ucloudToInternal(UCloudFile.create(path.path))
        }

        val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(path.path))

        val (_, outs) = nativeFS.openForWriting(
            tmpInternalFile,
            conflictPolicy,
            truncate = false,
            offset = offset
        )

        outs.use {
            payload.copyTo(outs)
        }

        if (conflictPolicy == WriteConflictPolicy.REPLACE && offset + payload.totalBytesRead >= totalSize) {
            nativeFS.move(tmpInternalFile, internalFile, WriteConflictPolicy.REPLACE)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

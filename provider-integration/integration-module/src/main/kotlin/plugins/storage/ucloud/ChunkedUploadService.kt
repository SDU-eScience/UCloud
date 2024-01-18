package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.LinuxOutputStream
import dk.sdu.cloud.utils.copyTo
import io.ktor.utils.io.*

class ChunkedUploadService(
    private val openFileDescriptors: OpenFileDescriptors,
) {
    suspend fun receiveChunk(
        path: UCloudFile,
        offset: Long,
        totalSize: Long,
        payload: ByteReadChannel,
        conflictPolicy: WriteConflictPolicy
    ) {
        val descriptor = openFileDescriptors.get(path.path)
        val stream = LinuxOutputStream(descriptor.handle)

        payload.copyTo(stream)
        descriptor.release()

        if (offset + payload.totalBytesRead >= totalSize) {
            openFileDescriptors.close(descriptor, conflictPolicy)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

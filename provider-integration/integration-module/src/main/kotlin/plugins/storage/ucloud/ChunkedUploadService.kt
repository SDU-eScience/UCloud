package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.LinuxOutputStream
import dk.sdu.cloud.utils.copyTo
import io.ktor.utils.io.*

class ChunkedUploadService(
    private val openFileDescriptors: UploadDescriptors
) {
    suspend fun receiveChunk(
        target: UCloudFile,
        offset: Long,
        payload: ByteReadChannel,
        conflictPolicy: WriteConflictPolicy,
        shouldClose: Boolean = false,
        modifiedAt: Long? = null
    ): Boolean {
        println("Retrieving file descriptor ${target.path} shouldClose=${shouldClose}")
        val descriptor = openFileDescriptors.get(target.path, offset, modifiedAt = modifiedAt)
        val stream = LinuxOutputStream(descriptor.handle)

        println("Receiving data for ${target.path} shouldClose=${shouldClose}")
        payload.copyTo(stream)

        if (shouldClose) {
            openFileDescriptors.close(descriptor, conflictPolicy, modifiedAt = modifiedAt)
        }
        descriptor.release()
        println("Released ${target.path}")

        return shouldClose
    }

    companion object : Loggable {
        override val log = logger()
    }
}

package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.parent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.LinuxFileHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class OpenFileDescriptor(
    val path: String,
    val handle: LinuxFileHandle,
    var lastUsed: Long,
    var inUse: Boolean
)

fun OpenFileDescriptor.release() {
    inUse = false
    lastUsed = Time.now()
}

class OpenFileDescriptors(
    private val pathConverter: PathConverter,
    private val nativeFS: NativeFS,
) {
    private val openDescriptors = mutableListOf<OpenFileDescriptor>()

    fun startMonitoringLoop() {
        ProcessingScope.launch {
            while (true) {
                val closed = mutableListOf<OpenFileDescriptor>()
                openDescriptors.forEach { descriptor ->
                    if (!descriptor.inUse && Time.now() > descriptor.lastUsed + 10_000) {
                        descriptor.handle.close()
                        closed.add(descriptor)
                    }
                }
                openDescriptors.removeAll(closed)
                delay(1000)
            }
        }
    }

    suspend fun get(path: String, conflictPolicy: WriteConflictPolicy? = null): OpenFileDescriptor {
        val internalFile = pathConverter.ucloudToInternal(UCloudFile.create("$path.part"))

        val found = openDescriptors.find { it.path == internalFile.path }
        if (found != null) {
            found.inUse = true
            return found
        }

        val (newPath, handle) = nativeFS.openForWritingWithHandle(
            internalFile,
            WriteConflictPolicy.REPLACE,
            truncate = false
        )

        val resolvedPath = internalFile.parent().path + newPath

        val newDescriptor = OpenFileDescriptor(resolvedPath, handle, Time.now(), true)
        openDescriptors.add(
            newDescriptor
        )

        return newDescriptor
    }

    suspend fun close(descriptor: OpenFileDescriptor, conflictPolicy: WriteConflictPolicy) {
        if (descriptor.inUse) return

        val tmpInternalFile = InternalFile(descriptor.path)
        val finalInternalFile = InternalFile(descriptor.path.removeSuffix(".part"))

        nativeFS.move(tmpInternalFile, finalInternalFile, conflictPolicy)

        descriptor.handle.close()
        openDescriptors.remove(descriptor)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
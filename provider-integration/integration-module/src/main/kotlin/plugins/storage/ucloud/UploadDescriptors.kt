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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UploadDescriptor(
    val partialPath: String,
    val targetPath: String,
    val handle: LinuxFileHandle,
    var lastUsed: Long,
    var inUse: Boolean
)

fun UploadDescriptor.release() {
    inUse = false
    lastUsed = Time.now()
}

class UploadDescriptors(
    private val pathConverter: PathConverter,
    private val nativeFS: NativeFS,
) {
    private val openDescriptors = mutableListOf<UploadDescriptor>()
    private val descriptorsMutex = Mutex()

    fun startMonitoringLoop() {
        ProcessingScope.launch {
            while (true) {
                descriptorsMutex.withLock {
                    val closed = mutableListOf<UploadDescriptor>()
                    openDescriptors.forEach { descriptor ->
                        if (!descriptor.inUse && Time.now() > descriptor.lastUsed + 10_000) {
                            descriptor.handle.close()
                            closed.add(descriptor)
                        }
                    }
                    openDescriptors.removeAll(closed)
                }

                delay(2000)
            }
        }
    }

    suspend fun get(path: String, truncate: Boolean = false): UploadDescriptor {
        descriptorsMutex.withLock {
            val internalTargetFile = pathConverter.ucloudToInternal(UCloudFile.create(path))
            val internalPartialFile = pathConverter.ucloudToInternal(UCloudFile.create("$path.ucloud_part"))

            val found = openDescriptors.find { it.partialPath == internalPartialFile.path }
            if (found != null) {
                found.inUse = true
                return found
            }

            val (newName, handle) = nativeFS.openForWritingWithHandle(
                internalPartialFile,
                WriteConflictPolicy.REPLACE,
                truncate = truncate
            )

            val resolvedPartialPath = internalPartialFile.parent().path + newName

            val newDescriptor = UploadDescriptor(resolvedPartialPath, internalTargetFile.path, handle, Time.now(), true)
            openDescriptors.add(
                newDescriptor
            )

            return newDescriptor
        }
    }

    suspend fun close(descriptor: UploadDescriptor, conflictPolicy: WriteConflictPolicy) {
        println("closing file descriptor")

        if (descriptor.inUse) return

        val partialInternalFile = InternalFile(descriptor.partialPath)
        val targetInternalFile = InternalFile(descriptor.targetPath)

        nativeFS.move(partialInternalFile, targetInternalFile, conflictPolicy)

        descriptor.handle.close()
        openDescriptors.remove(descriptor)
        println("closed file descriptor")
    }

    companion object : Loggable {
        override val log = logger()
    }
}

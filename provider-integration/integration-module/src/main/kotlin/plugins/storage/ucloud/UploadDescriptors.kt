package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.calls.client.AtomicInteger
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
import libc.clib

data class UploadDescriptor(
    val partialPath: String,
    val targetPath: String,
    val handle: LinuxFileHandle,
    var lastUsed: Long,
    val inUse: Mutex,
    val waiting: AtomicInteger,
)

fun UploadDescriptor.release() {
    lastUsed = Time.now()
    inUse.unlock()
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
                        if (Time.now() > descriptor.lastUsed + 10_000 && descriptor.inUse.tryLock()) {
                            if (descriptor.waiting.get() != 0) {
                                descriptor.inUse.unlock()
                            } else {
                                descriptor.handle.close()
                                closed.add(descriptor)
                            }
                        }
                    }
                    openDescriptors.removeAll(closed)
                }

                delay(2000)
            }
        }
    }

    suspend fun get(
        path: String,
        offset: Long? = null,
        truncate: Boolean = false,
        modifiedAt: Long? = null
    ): UploadDescriptor {
        val internalTargetFile = pathConverter.ucloudToInternal(UCloudFile.create(path))
        val internalPartialFile = pathConverter.ucloudToInternal(UCloudFile.create("$path.ucloud_part"))

        val descriptor = descriptorsMutex.withLock {
            val found = openDescriptors.find { it.partialPath == internalPartialFile.path }
            if (found != null) {
                found.waiting.getAndIncrement()
                return@withLock found
            }

            val (newName, handle) = nativeFS.openForWritingWithHandle(
                internalPartialFile,
                WriteConflictPolicy.REPLACE,
                truncate = truncate,
                offset = offset
            )

            if (modifiedAt != null) {
                clib.modifyTimestamps(handle.fd, modifiedAt)
            }

            val resolvedPartialPath = internalPartialFile.parent().path + newName

            val newDescriptor = UploadDescriptor(
                partialPath = resolvedPartialPath,
                targetPath = internalTargetFile.path,
                handle = handle,
                lastUsed = Time.now(),
                inUse = Mutex(),
                waiting = AtomicInteger(0)
            )
            newDescriptor.waiting.getAndIncrement()
            openDescriptors.add(newDescriptor)
            return@withLock newDescriptor
        }

        descriptor.inUse.lock()
        descriptor.waiting.getAndDecrement()
        if (offset != null) {
            descriptor.handle.seek(offset)
        }
        return descriptor
    }

    suspend fun close(descriptor: UploadDescriptor, conflictPolicy: WriteConflictPolicy, modifiedAt: Long? = null) {
        val partialInternalFile = InternalFile(descriptor.partialPath)
        val targetInternalFile = InternalFile(descriptor.targetPath)

        nativeFS.move(partialInternalFile, targetInternalFile, conflictPolicy)

        if (modifiedAt != null) {
            clib.modifyTimestamps(descriptor.handle.fd, modifiedAt)
        }

        descriptorsMutex.withLock {
            descriptor.handle.close()
            openDescriptors.remove(descriptor)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

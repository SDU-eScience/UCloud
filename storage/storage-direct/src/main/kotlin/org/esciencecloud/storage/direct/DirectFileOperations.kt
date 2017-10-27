package org.esciencecloud.storage.direct

import com.ceph.rados.IoCTX
import com.ceph.rados.Rados
import org.esciencecloud.storage.FileOperations
import org.esciencecloud.storage.FileType
import org.esciencecloud.storage.StoragePath
import java.io.InputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

abstract class DirectFileOperations( // Really easy way of getting rid of compilation errors ;-)
        val rados: Rados,
        private val ioCtx: IoCTX,
        private val executor: ExecutorService
) : FileOperations {
    override fun put(path: StoragePath, source: InputStream) {
        // TODO Try with resource on source
        val uuid = UUID.randomUUID()
        val physicalName = uuid.toString()

        if (!DataObjects.canUserWriteAt(path, "foobar")) {
            throw IllegalStateException("Not allowed, foobar!") // TODO
        }

        var ptr = 0L
        var idx = 0
        var hasMoreData = true

        val jobs = ArrayList<Future<*>>()
        while (hasMoreData) {
            // TODO break when we can't read more
            val blockSize = 1024 * 4096L

            val buffer = ByteArray(blockSize.toInt())
            var internalPtr = 0
            while (internalPtr < buffer.size) {
                val bytesRead = source.read(buffer, internalPtr, buffer.size - internalPtr)
                if (bytesRead == -1) {
                    hasMoreData = false
                    break
                }
                internalPtr += bytesRead
            }

            val resizedBuffer = if (internalPtr < buffer.size) buffer.sliceArray(0 until internalPtr) else buffer
            val currentIdx = idx
            jobs.add(executor.submit { ioCtx.write("$physicalName-$currentIdx", resizedBuffer) })
            // TODO We need to await for all of the executors to finish.

            idx++
            ptr += internalPtr
        }
        jobs.forEach { it.get() } // await all

        DataObject.new {
            this.name = path.path
            this.physicalPath = physicalName
            this.type = FileType.FILE
        }.flush()
    }
}
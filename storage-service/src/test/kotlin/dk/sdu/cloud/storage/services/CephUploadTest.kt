package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.storage.SERVICE_USER
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFSUserDao
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals

class CephUploadTest {
    @Ignore
    @Test
    fun `test storage events for new file`() {
        val userDao = CephFSUserDao(true)
        val fsRoot = Files.createTempDirectory("ceph-fs").toFile()
        val cephFs = CephFileSystem(userDao, fsRoot.absolutePath)
        val factory = CephFSCommandRunnerFactory(userDao, true)
        val owner = SERVICE_USER

        factory.withContext(owner) { ctx ->
            run {
                val result = cephFs.openForWriting(ctx, "/file.txt", true)
                assertEquals(0, result.statusCode)
                assertEquals(1, result.value.size)
                val createdEvent = result.value.single()
                assertEquals(FileType.FILE, createdEvent.fileType)
                assertEquals(0, createdEvent.size)
            }

            run {
                val result = cephFs.write(ctx) { it.write(ByteArray(10)) }
                assertEquals(0, result.statusCode)
                assertEquals(1, result.value.size)
                val createdEvent = result.value.single()
                assertEquals(FileType.FILE, createdEvent.fileType)
                assertEquals(10, createdEvent.size)
            }
        }
    }
}

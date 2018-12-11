package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.unixfs.UnixFSUserDao
import dk.sdu.cloud.file.services.unixfs.UnixFileSystem
import dk.sdu.cloud.file.services.withBlockingContext
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class UnixUploadTest {
    @Ignore
    @Test
    fun `test storage events for new file`() {
        val userDao = UnixFSUserDao(true)
        val fsRoot = Files.createTempDirectory("ceph-fs").toFile()
        val cephFs = UnixFileSystem(userDao, fsRoot.absolutePath)
        val factory = UnixFSCommandRunnerFactory(userDao, true)
        val owner = SERVICE_USER

        factory.withBlockingContext(owner) { ctx ->
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

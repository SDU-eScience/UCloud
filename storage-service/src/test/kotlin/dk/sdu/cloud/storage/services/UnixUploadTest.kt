package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.size
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.storage.util.simpleStorageUserDao
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class UnixUploadTest {
    @Ignore
    @Test
    fun `test storage events for new file`() {
        val userDao = simpleStorageUserDao()
        val fsRoot = Files.createTempDirectory("ceph-fs").toFile()
        val factory = LinuxFSRunnerFactory(userDao)
        val cephFs = LinuxFS(factory, fsRoot, userDao)
        val owner = SERVICE_USER

        factory.withBlockingContext(owner) { ctx ->
            run {
                val result = cephFs.openForWriting(ctx, "/file.txt", true)
                assertEquals(0, result.statusCode)
                assertEquals(1, result.value.size)
                val createdEvent = result.value.single()
                assertEquals(FileType.FILE, createdEvent.file.fileType)
                assertEquals(0, createdEvent.file.size)
            }

            run {
                val result = cephFs.write(ctx) { it.write(ByteArray(10)) }
                assertEquals(0, result.statusCode)
                assertEquals(1, result.value.size)
                val createdEvent = result.value.single()
                assertEquals(FileType.FILE, createdEvent.file.fileType)
                assertEquals(10, createdEvent.file.size)
            }
        }
    }
}

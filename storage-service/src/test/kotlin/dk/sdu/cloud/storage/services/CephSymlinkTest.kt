package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.storage.SERVICE_USER
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFSUserDao
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CephSymlinkTest {
    @Test
    fun `test creating a symlink`() {
        val userDao = CephFSUserDao(true)
        val fsRoot = Files.createTempDirectory("ceph-fs").toFile()
        val cephFs = CephFileSystem(userDao, fsRoot.absolutePath)
        val factory = CephFSCommandRunnerFactory(userDao, true)
        val owner = SERVICE_USER

        factory.withContext(owner) { ctx ->
            val targetPath = "/target"
            val linkPath = "/link"

            cephFs.openForWriting(ctx, targetPath, false)
            val fileCreated = cephFs.write(ctx) {
                it.write("Hello, World!".toByteArray())
            }.value.single()

            val symlinkCreated = cephFs.createSymbolicLink(ctx, targetPath, linkPath).value.single()

            assertFalse(fileCreated.isLink)
            assertEquals(FileType.FILE, fileCreated.fileType)
            assertEquals(targetPath, fileCreated.path)

            assertTrue(symlinkCreated.isLink)
            assertEquals(fileCreated.id, symlinkCreated.linkTargetId)
            assertEquals(targetPath, symlinkCreated.linkTarget)
        }
    }
}
package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.link
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.UIDLookupService
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.file.util.unwrap
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.simpleStorageUserDao
import dk.sdu.cloud.storage.util.touch
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnixSymlinkTest {
    lateinit var userDao: UIDLookupService
    lateinit var fsRoot: File
    lateinit var factory: LinuxFSRunnerFactory
    lateinit var cephFs: LinuxFS
    lateinit var owner: String

    @BeforeTest
    fun init() {
        userDao = simpleStorageUserDao()
        fsRoot = Files.createTempDirectory("ceph-fs").toFile().apply {
            mkdir("home") {
                mkdir("user") {
                    touch("target")
                }
            }
        }
        factory = LinuxFSRunnerFactory()
        cephFs = LinuxFS(factory, userDao)
        owner = SERVICE_USER
    }

    @Ignore
    @Test
    fun `test creating a symlink`() {
        factory.withBlockingContext(owner) { ctx ->
            val targetPath = "/target"
            val linkPath = "/link"

            cephFs.openForWriting(ctx, targetPath, false)
            val fileCreated = cephFs.write(ctx) {
                it.write("Hello, World!".toByteArray())
            }.value.single()

            val symlinkCreated = cephFs.createSymbolicLink(ctx, targetPath, linkPath).value.single()

            assertFalse(fileCreated.file.link)
            assertEquals(FileType.FILE, fileCreated.file.fileType)
            assertEquals(targetPath, fileCreated.file.path)

            assertTrue(symlinkCreated.file.link)
        }
    }

    @Test
    fun `test creating a dead link and list parent directory`() {
        factory.withBlockingContext(owner) { ctx ->
            val targetPath = "/home/user/target"
            val linkPath = "/home/user/link"

            println("Creating")
            cephFs.createSymbolicLink(ctx, targetPath, linkPath).value.single()

            println("Deleting")
            cephFs.delete(ctx, targetPath)

            println("Listing")
            val ls = cephFs.listDirectory(ctx, "/home/user", FileAttribute.values().toSet()).unwrap()
            println(ls)
            assertEquals(1, ls.size)
        }
    }
}

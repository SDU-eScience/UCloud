package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.services.cephfs.*
import io.mockk.Runs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShareServiceTest {
    fun File.mkdir(name: String, closure: File.() -> Unit) {
        val f = File(this, name)
        f.mkdir()
        f.closure()
    }

    fun File.touch(name: String) {
        File(this, name).writeText("Hello!")
    }

    fun createFileSystem(): File {
        val fsRoot = Files.createTempDirectory("share-service-test").toFile()
        fsRoot.apply {
            mkdir("home") {
                (1..10).map { "user$it" }.forEach {
                    mkdir(it) {
                        mkdir("PleaseShare") {
                            touch("file.txt")
                        }
                    }
                }
            }
        }
        return fsRoot
    }

    fun createUsers(): CloudToCephFsDao {
        val dao = mockk<CloudToCephFsDao>()
        (1..10).map { "user$it" }.forEach {
            every { dao.findUnixUser(it) } returns it
            every { dao.findCloudUser(it) } returns it
        }
        return dao
    }

    @Test
    fun testGrantShare() {
        val processRunner = mockk<CephFSProcessRunner>()
        every { processRunner.runAsUser(any(), any(), any()) } just Runs

        val fileAclService = mockk<FileACLService>()
        every { fileAclService.createEntry(any(), any(), any(), any(), any(), any()) } just Runs

        val dao = createUsers()
        val fsRoot = createFileSystem()

        val service = CephFSFileSystemService(
            dao,
            processRunner,
            fileAclService,
            mockk(),
            mockk(),
            fsRoot.absolutePath,
            true
        )

        service.grantRights("user1", "user2", "/home/user1/PleaseShare", setOf(AccessRight.READ, AccessRight.EXECUTE))
        verify {
            fileAclService.createEntry(
                "user1",
                "user2",
                File(fsRoot, "home/user1").absolutePath,
                setOf(AccessRight.EXECUTE),
                false,
                false
            )

            fileAclService.createEntry(
                "user1",
                "user2",
                File(fsRoot, "home/user1/PleaseShare").absolutePath,
                setOf(AccessRight.READ, AccessRight.EXECUTE),
                defaultList = true,
                recursive = true
            )

            fileAclService.createEntry(
                "user1",
                "user2",
                File(fsRoot, "home/user1/PleaseShare").absolutePath,
                setOf(AccessRight.READ, AccessRight.EXECUTE),
                defaultList = false,
                recursive = true
            )
        }
    }

    @Test(expected = ShareException.PermissionException::class)
    fun testGrantShareWithMissingPermissions() {
        val processRunner = mockk<CephFSProcessRunner>()

        val fileAclService = mockk<FileACLService>()
        every { fileAclService.createEntry(any(), any(), any(), any(), any(), any()) } throws
                ShareException.PermissionException()

        val dao = createUsers()
        val fsRoot = createFileSystem()

        val service = CephFSFileSystemService(
            dao,
            processRunner,
            fileAclService,
            mockk(),
            mockk(),
            fsRoot.absolutePath,
            true
        )

        service.grantRights("user1", "user2", "/home/user1/PleaseShare", setOf(AccessRight.READ, AccessRight.EXECUTE))
    }

    @Test(expected = ShareException.PermissionException::class)
    fun testGrantShareWithLowLevelFailure() {
        val processRunner = mockk<CephFSProcessRunner>()
        every {
            processRunner.runAsUserWithResultAsInMemoryString(
                any(),
                any(),
                any()
            )
        } returns InMemoryProcessResultAsString(1, "", "")

        val dao = createUsers()
        val fileAclService = FileACLService(dao, processRunner, true)
        val fsRoot = createFileSystem()

        val service = CephFSFileSystemService(
            dao,
            processRunner,
            fileAclService,
            mockk(),
            mockk(),
            fsRoot.absolutePath,
            true
        )

        service.grantRights("user1", "user2", "/home/user1/PleaseShare", setOf(AccessRight.READ, AccessRight.EXECUTE))
    }

    @Test
    fun testRevoke() {
        val processRunner = mockk<CephFSProcessRunner>()
        every { processRunner.runAsUser(any(), any(), any()) } just Runs

        val fileAclService = mockk<FileACLService>()
        every { fileAclService.createEntry(any(), any(), any(), any(), any(), any()) } just Runs
        every { fileAclService.removeEntry(any(), any(), any(), any(), any()) } just Runs

        val dao = createUsers()
        val fsRoot = createFileSystem()

        val service = CephFSFileSystemService(
            dao,
            processRunner,
            fileAclService,
            mockk(),
            mockk(),
            fsRoot.absolutePath,
            true
        )

        service.grantRights("user1", "user2", "/home/user1/PleaseShare", setOf(AccessRight.READ, AccessRight.EXECUTE))
        service.revokeRights("user1", "user2", "/home/user1/PleaseShare")

        verify {
            fileAclService.removeEntry(
                "user1",
                "user2",
                File(fsRoot, "home/user1/PleaseShare").absolutePath,
                false,
                true
            )

            fileAclService.removeEntry(
                "user1",
                "user2",
                File(fsRoot, "home/user1/PleaseShare").absolutePath,
                true,
                true
            )
        }
    }
}
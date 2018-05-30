package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.services.cephfs.*
import io.mockk.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShareServiceTest {
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
        val fileAclService = mockk<FileACLService>()
        every { fileAclService.createEntry(any(), any(), any(), any(), any(), any()) } just Runs

        val fsRoot = createFileSystem()
        val service =
            cephFSWithRelaxedMocks(fsRoot.absolutePath, fileACLService = fileAclService)

        val ctx = service.openContext("user1")
        service.grantRights(ctx, "user2", "/home/user1/PleaseShare", setOf(AccessRight.READ, AccessRight.EXECUTE))
        verify {
            fileAclService.createEntry(
                ctx,
                "user2",
                File(fsRoot, "home/user1").absolutePath,
                setOf(AccessRight.EXECUTE),
                false,
                false
            )

            fileAclService.createEntry(
                ctx,
                "user2",
                File(fsRoot, "home/user1/PleaseShare").absolutePath,
                setOf(AccessRight.READ, AccessRight.EXECUTE),
                defaultList = true,
                recursive = true
            )

            fileAclService.createEntry(
                ctx,
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
        val fileAclService = mockk<FileACLService>()
        every { fileAclService.createEntry(any(), any(), any(), any(), any(), any()) } throws
                ShareException.PermissionException()

        val fsRoot = createFileSystem()
        val service =
            cephFSWithRelaxedMocks(fsRoot.absolutePath, fileACLService = fileAclService)

        service.grantRights(
            service.openContext("user1"),
            "user2",
            "/home/user1/PleaseShare",
            setOf(AccessRight.READ, AccessRight.EXECUTE)
        )
    }

    @Test(expected = ShareException.PermissionException::class)
    fun testGrantShareWithLowLevelFailure() {
        val processRunner = mockk<CephFSProcessRunner>()
        val processRunnerFactory: ProcessRunnerFactory = { processRunner }
        every {
            processRunner.runWithResultAsInMemoryString(
                any(),
                any()
            )
        } returns InMemoryProcessResultAsString(1, "", "")

        val dao = createUsers()
        val fileAclService = FileACLService(dao, true)
        val fsRoot = createFileSystem()

        val service = cephFSWithRelaxedMocks(
            fsRoot.absolutePath,
            cloudToCephFsDao = dao,
            fileACLService = fileAclService,
            processRunner = processRunnerFactory
        )

        service.grantRights(
            service.openContext("user1"),
            "user2",
            "/home/user1/PleaseShare",
            setOf(AccessRight.READ, AccessRight.EXECUTE)
        )
    }

    @Test
    fun testRevoke() {
        val fileAclService = mockk<FileACLService>()
        every { fileAclService.createEntry(any(), any(), any(), any(), any(), any()) } just Runs
        every { fileAclService.removeEntry(any(), any(), any(), any(), any()) } just Runs

        val fsRoot = createFileSystem()
        val service =
            cephFSWithRelaxedMocks(fsRoot.absolutePath, fileACLService = fileAclService)

        val ctx = service.openContext("user1")
        service.grantRights(
            ctx,
            "user2",
            "/home/user1/PleaseShare",
            setOf(AccessRight.READ, AccessRight.EXECUTE)
        )
        service.revokeRights(ctx, "user2", "/home/user1/PleaseShare")

        verify {
            fileAclService.removeEntry(
                ctx,
                "user2",
                File(fsRoot, "home/user1/PleaseShare").absolutePath,
                defaultList = false,
                recursive = true
            )

            fileAclService.removeEntry(
                ctx,
                "user2",
                File(fsRoot, "home/user1/PleaseShare").absolutePath,
                defaultList = true,
                recursive = true
            )
        }
    }
}
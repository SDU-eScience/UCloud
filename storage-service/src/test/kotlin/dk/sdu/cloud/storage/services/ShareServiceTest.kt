package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.services.cephfs.CloudToCephFsDao
import dk.sdu.cloud.storage.services.cephfs.FileACLService
import io.mockk.*
import org.junit.Test
import java.io.File

class ShareServiceTest {
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

        val fsRoot = createDummyFS()
        val service =
            cephFSWithRelaxedMocks(fsRoot.absolutePath, fileACLService = fileAclService)

        service.withContext("user1") { ctx ->
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
    }

    @Test(expected = ShareException.PermissionException::class)
    fun testGrantShareWithMissingPermissions() {
        val fileAclService = mockk<FileACLService>()
        every { fileAclService.createEntry(any(), any(), any(), any(), any(), any()) } throws
                ShareException.PermissionException()

        val fsRoot = createDummyFS()
        val service =
            cephFSWithRelaxedMocks(fsRoot.absolutePath, fileACLService = fileAclService)

        service.withContext("user1") {
            service.grantRights(
                it,
                "user2",
                "/home/user1/PleaseShare",
                setOf(AccessRight.READ, AccessRight.EXECUTE)
            )
        }
    }

    @Test
    fun testRevoke() {
        val fileAclService = mockk<FileACLService>()
        every { fileAclService.createEntry(any(), any(), any(), any(), any(), any()) } just Runs
        every { fileAclService.removeEntry(any(), any(), any(), any(), any()) } just Runs

        val fsRoot = createDummyFS()
        val service =
            cephFSWithRelaxedMocks(fsRoot.absolutePath, fileACLService = fileAclService)

        service.withContext("user1") { ctx ->
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
}
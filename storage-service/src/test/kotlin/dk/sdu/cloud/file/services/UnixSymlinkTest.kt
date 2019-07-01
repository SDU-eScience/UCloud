package dk.sdu.cloud.file.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.link
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.acl.AclHibernateDao
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.util.unwrap
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.file.util.mkdir
import dk.sdu.cloud.file.util.simpleStorageUserDao
import dk.sdu.cloud.file.util.touch
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

        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val db = micro.hibernateDatabase
        val homeFolderService = HomeFolderService(ClientMock.authenticatedClient)
        ClientMock.mockCall(UserDescriptions.lookupUsers) {
            TestCallResult.Ok(
                LookupUsersResponse(it.users.map { it to UserLookup(it, it.hashCode().toLong(), Role.USER) }.toMap())
            )
        }
        val aclService = AclService(db, AclHibernateDao(), homeFolderService)
        cephFs = LinuxFS(fsRoot, aclService)
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

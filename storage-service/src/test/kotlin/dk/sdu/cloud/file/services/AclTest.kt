package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.services.acl.AclHibernateDao
import dk.sdu.cloud.file.services.acl.AccessRights
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AclTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService<*>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)
        aclService = AclService(micro.hibernateDatabase, AclHibernateDao(), MockedHomeFolderService, { it.normalize() })

        micro.hibernateDatabase.withTransaction {
            it.createNativeQuery("CREATE ALIAS IF NOT EXISTS REVERSE AS \$\$ String reverse(String s) { return new StringBuilder(s).reverse().toString(); } \$\$;").executeUpdate()
        }
    }

    @Test
    fun `empty acls`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        val instance = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(instance, { it.size }, 1)
        assertThatPropertyEquals(instance[userHome], { it!!.size }, 0)

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertFalse(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertFalse(aclService.hasPermission(userHome, notUser, AccessRight.READ))
    }

    @Test
    fun `add user to acl`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.updatePermissions(userHome, notUser, AccessRights.READ_WRITE)

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val user = it[it.keys.single()]!!.single()
            user.permissions == AccessRights.READ_WRITE && user.username == notUser
        }
    }

    @Test
    fun `add user to acl and downgrade`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.updatePermissions(userHome, notUser, AccessRights.READ_WRITE)

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        aclService.updatePermissions(userHome, notUser, AccessRights.READ_ONLY)
        assertFalse(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val user = it[it.keys.single()]!!.single()
            user.permissions == AccessRights.READ_ONLY && user.username == notUser
        }
    }

    @Test
    fun `add and remove user`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.updatePermissions(userHome, notUser, AccessRights.READ_WRITE)

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        aclService.revokePermission(userHome, notUser)
        assertFalse(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertFalse(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list[userHome]!!) { it.isEmpty() }
    }

    @Test
    fun `test deep file for owner`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"

        (0 until 10).map { (arrayOf(userHome) + Array(it) { "dir-$it" }).joinToString("/") }.forEach { path ->
            assertTrue(aclService.hasPermission(path, username, AccessRight.WRITE))
            assertTrue(aclService.hasPermission(path, username, AccessRight.READ))
        }
    }

    @Test
    fun `test deep file for owner and shared`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.updatePermissions(userHome, notUser, AccessRights.READ_WRITE)
        (0 until 10).map { (arrayOf(userHome) + Array(it) { "dir-$it" }).joinToString("/") }.forEach { path ->
            assertTrue(aclService.hasPermission(path, username, AccessRight.WRITE))
            assertTrue(aclService.hasPermission(path, username, AccessRight.READ))

            assertTrue(aclService.hasPermission(path, notUser, AccessRight.WRITE))
            assertTrue(aclService.hasPermission(path, notUser, AccessRight.READ))
        }
    }

    @Test
    fun `test deep file for owner and shared - not root`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.updatePermissions("$userHome/dir-0/dir-1/dir-2", notUser, AccessRights.READ_WRITE)
        (0 until 10).map { (arrayOf(userHome) + Array(it) { "dir-$it" }).joinToString("/") }.forEach { path ->
            assertTrue(aclService.hasPermission(path, username, AccessRight.WRITE))
            assertTrue(aclService.hasPermission(path, username, AccessRight.READ))
        }

        (0 until 10).map { (arrayOf(userHome) + Array(it) { "dir-$it" }).joinToString("/") }.drop(3).forEach { path ->
            assertTrue(aclService.hasPermission(path, notUser, AccessRight.WRITE))
            assertTrue(aclService.hasPermission(path, notUser, AccessRight.READ))
        }
    }

    @Test
    fun `test non-normalized path`() = runBlocking {
        assertTrue(aclService.hasPermission("/home/user/../user/././././/", "user", AccessRight.WRITE))
        assertTrue(aclService.hasPermission("/home/user/../user/././././/", "user", AccessRight.READ))
    }

    @Test
    fun `test root permissions`() = runBlocking {
        assertFalse(aclService.hasPermission("/", "user", AccessRight.READ))
        assertFalse(aclService.hasPermission("/", "user", AccessRight.WRITE))

        assertFalse(aclService.hasPermission("/home", "user", AccessRight.READ))
        assertFalse(aclService.hasPermission("/home", "user", AccessRight.WRITE))

        assertFalse(aclService.hasPermission("/workspaces", "user", AccessRight.READ))
        assertFalse(aclService.hasPermission("/workspaces", "user", AccessRight.WRITE))
    }

    @Test
    fun `test very long path`() = runBlocking {
        // Note: that this folder is not in /home/user/
        val path = "/home/user" + String(CharArray(4096) { 'a' })
        assertFalse(aclService.hasPermission(path, "user", AccessRight.WRITE))
        assertFalse(aclService.hasPermission(path, "user", AccessRight.READ))
    }

    @Test
    fun `test path with new lines`() = runBlocking {
        // Note: that this folder is not in /home/user/
        val path = "/home/user\n\n\nfoo"
        assertFalse(aclService.hasPermission(path, "user", AccessRight.WRITE))
        assertFalse(aclService.hasPermission(path, "user", AccessRight.READ))
    }

    @Test
    fun `add and remove user multiple files`() = runBlocking {
        val username = "user"
        val folderA = "/home/$username/a"
        val folderB = "/home/$username/b"
        val notUser = "notUser"

        aclService.updatePermissions(folderA, notUser, AccessRights.READ_WRITE)
        aclService.updatePermissions(folderB, notUser, AccessRights.READ_WRITE)

        assertTrue(aclService.hasPermission(folderA, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(folderA, notUser, AccessRight.READ))

        assertTrue(aclService.hasPermission(folderB, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(folderB, notUser, AccessRight.READ))

        aclService.revokePermission(folderA, notUser)
        assertFalse(aclService.hasPermission(folderA, notUser, AccessRight.WRITE))
        assertFalse(aclService.hasPermission(folderA, notUser, AccessRight.READ))

        assertTrue(aclService.hasPermission(folderB, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(folderB, notUser, AccessRight.READ))

        val listA = aclService.listAcl(listOf(folderA))
        assertThatPropertyEquals(listA, { it.size }, 1)
        assertThatPropertyEquals(listA[folderA], { it!!.size }, 0)

        val listB = aclService.listAcl(listOf(folderB))
        assertThatPropertyEquals(listB, { it.size }, 1)
        assertThatPropertyEquals(listB[folderB], { it!!.size }, 1)
    }

    @Test
    fun `add user to acl several times`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        repeat(10) {
            aclService.updatePermissions(userHome, notUser, AccessRights.READ_WRITE)
        }

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val user = it[it.keys.single()]!!.single()
            user.permissions == AccessRights.READ_WRITE && user.username == notUser
        }
    }

    @Test
    fun `test adding user and moving file`() = runBlocking {
        val username = "user"
        val sharedFolder = "/home/$username/shared"
        val sharedFolderNew = "/home/$username/sharedNew"
        val notUser = "notUser"

        aclService.updatePermissions(sharedFolder, notUser, AccessRights.READ_WRITE)
        aclService.handleFilesMoved(listOf(sharedFolder), listOf(sharedFolderNew))

        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.WRITE))
        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.READ))

        assertTrue(aclService.hasPermission(sharedFolderNew, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(sharedFolderNew, notUser, AccessRight.READ))
    }

    @Test
    fun `test adding user and moving multiple files`() = runBlocking {
        val username = "user"
        val sharedFolder = "/home/$username/shared"
        val sharedFolderNew = "/home/$username/sharedNew"
        val notUser = "notUser"

        aclService.updatePermissions(sharedFolder, notUser, AccessRights.READ_WRITE)
        aclService.handleFilesMoved(
            listOf(sharedFolder, "$sharedFolder/file"),
            listOf(sharedFolderNew, "$sharedFolderNew/file")
        )

        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.WRITE))
        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.READ))

        assertTrue(aclService.hasPermission(sharedFolderNew, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(sharedFolderNew, notUser, AccessRight.READ))
    }

    @Test
    fun `test adding user and moving multiple files 2`() = runBlocking {
        val username = "user"
        val sharedFolder = "/home/$username/shared"
        val sharedFolderNew = "/home/$username/sharedNew"
        val notUser = "notUser"

        aclService.updatePermissions(sharedFolder, notUser, AccessRights.READ_WRITE)
        aclService.handleFilesMoved(
            listOf(sharedFolder, "$sharedFolder/file"),
            listOf(sharedFolderNew, "$sharedFolderNew/file22")
        )

        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.WRITE))
        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.READ))

        assertTrue(aclService.hasPermission(sharedFolderNew, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(sharedFolderNew, notUser, AccessRight.READ))
    }

    @Test
    fun `test deleting files from share`() = runBlocking {
        val username = "user"
        val sharedFolder = "/home/$username/shared"
        val notUser = "notUser"

        aclService.updatePermissions(sharedFolder, notUser, AccessRights.READ_WRITE)
        aclService.handleFilesDeleted(
            Array(200) { "$sharedFolder/f-$it" }.toList() + listOf(sharedFolder)
        )

        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.WRITE))
        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.READ))
    }

    @Test
    fun `test merging of acls`() = runBlocking {
        val username = "user"
        val sharedFolder = "/home/$username/shared"
        val notUser = "notUser"

        aclService.updatePermissions(sharedFolder, notUser, AccessRights.READ_WRITE)

        assertThatInstance(
            aclService.listAcl(listOf(sharedFolder)),
            matcher = {
                it.size == 1
            }
        )
    }
}

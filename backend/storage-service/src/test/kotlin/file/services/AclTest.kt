package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.ACLEntity
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.AclEntryRequest
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.services.acl.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.*

// Testing not currently possible due to requirement for PostgreSQL database
@Ignore
class AclTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService
    private lateinit var metadataService: MetadataService

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        metadataService = MetadataService(AsyncDBSessionFactory(micro.databaseConfig), MetadataDao())
        aclService =
            AclService(metadataService, MockedHomeFolderService, ClientMock.authenticatedClient, mockk(relaxed = true))
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

        aclService.updateAcl(
            UpdateAclRequest(
                userHome,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val entry = it[it.keys.single()]!!.single()
            val entity = entry.entity
            entry.permissions == AccessRights.READ_WRITE && entity is ACLEntity.User && entity.username == notUser
        }
    }

    @Test
    fun `add user to acl and downgrade`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"
        aclService.updateAcl(
            UpdateAclRequest(
                userHome,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))
        aclService.updateAcl(
            UpdateAclRequest(
                userHome,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_ONLY))
            ),
            username
        )
        assertFalse(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val entry = it[it.keys.single()]!!.single()
            val entity = entry.entity
            entry.permissions == AccessRights.READ_ONLY && entity is ACLEntity.User && entity.username == notUser
        }
    }

    @Test
    fun `add and remove user`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.updateAcl(
            UpdateAclRequest(
                userHome,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))
        aclService.updateAcl(
            UpdateAclRequest(
                userHome,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE, revoke = true))
            ),
            username
        )
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
        aclService.updateAcl(
            UpdateAclRequest(
                userHome,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )
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

        aclService.updateAcl(
            UpdateAclRequest(
                "$userHome/dir-0/dir-1/dir-2",
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )
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
        assertTrue(aclService.hasPermission("/home/user/../user/././././", "user", AccessRight.WRITE))
        assertTrue(aclService.hasPermission("/home/user/../user/././././", "user", AccessRight.READ))
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
        aclService.updateAcl(
            UpdateAclRequest(
                folderA,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )
        aclService.updateAcl(
            UpdateAclRequest(
                folderB,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )

        assertTrue(aclService.hasPermission(folderA, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(folderA, notUser, AccessRight.READ))

        assertTrue(aclService.hasPermission(folderB, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(folderB, notUser, AccessRight.READ))
        aclService.updateAcl(
            UpdateAclRequest(
                folderA,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE, revoke = true))
            ),
            username
        )
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
            aclService.updateAcl(
                UpdateAclRequest(
                    userHome,
                    listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
                ),
                username
            )
        }

        assertTrue(aclService.hasPermission(userHome, username, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AccessRight.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AccessRight.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val entry = it[it.keys.single()]!!.single()
            val entity = entry.entity
            entry.permissions == AccessRights.READ_WRITE && entity is ACLEntity.User && entity.username == notUser
        }
    }

    @Test
    fun `test adding user and moving file`() = runBlocking {
        val username = "user"
        val sharedFolder = "/home/$username/shared"
        val sharedFolderNew = "/home/$username/sharedNew"
        val notUser = "notUser"
        aclService.updateAcl(
            UpdateAclRequest(
                sharedFolder,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )
        metadataService.runMoveAction(sharedFolder, sharedFolderNew) {}

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
        aclService.updateAcl(
            UpdateAclRequest(
                sharedFolder,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )
        metadataService.runMoveAction(sharedFolder, sharedFolderNew) {}

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
        aclService.updateAcl(
            UpdateAclRequest(
                sharedFolder,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )
        metadataService.runMoveAction(sharedFolder, sharedFolderNew) {}
        metadataService.runMoveAction("$sharedFolder/file", "$sharedFolderNew/file22") {}

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

        aclService.updateAcl(
            UpdateAclRequest(
                sharedFolder,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )
        metadataService.runDeleteAction(Array(200) { "$sharedFolder/f-$it" }.toList() + listOf(sharedFolder)) {}

        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.WRITE))
        assertFalse(aclService.hasPermission(sharedFolder, notUser, AccessRight.READ))
    }

    @Test
    fun `test merging of acls`() = runBlocking {
        val username = "user"
        val sharedFolder = "/home/$username/shared"
        val notUser = "notUser"

        aclService.updateAcl(
            UpdateAclRequest(
                sharedFolder,
                listOf(AclEntryRequest(ACLEntity.User(notUser), AccessRights.READ_WRITE))
            ),
            username
        )

        assertThatInstance(
            aclService.listAcl(listOf(sharedFolder)),
            matcher = {
                it.size == 1
            }
        )
    }
}

package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.services.acl.AclHibernateDao
import dk.sdu.cloud.file.services.acl.AclPermission
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatProperty
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
        aclService = AclService(micro.hibernateDatabase, AclHibernateDao(), MockedHomeFolderService)
    }

    @Test
    fun `empty acls`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"


        assertThatPropertyEquals(aclService.listAcl(listOf(userHome)), { it.size }, 0)

        assertTrue(aclService.hasPermission(userHome, username, AclPermission.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AclPermission.READ))

        assertFalse(aclService.hasPermission(userHome, notUser, AclPermission.WRITE))
        assertFalse(aclService.hasPermission(userHome, notUser, AclPermission.READ))
    }

    @Test
    fun `add user to acl`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.createOrUpdatePermission(userHome, notUser, AclPermission.WRITE)

        assertTrue(aclService.hasPermission(userHome, username, AclPermission.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AclPermission.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AclPermission.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AclPermission.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val user = it[it.keys.single()]!!.single()
            user.permission == AclPermission.WRITE && user.username == notUser
        }
    }

    @Test
    fun `add user to acl and downgrade`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.createOrUpdatePermission(userHome, notUser, AclPermission.WRITE)

        assertTrue(aclService.hasPermission(userHome, username, AclPermission.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AclPermission.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AclPermission.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AclPermission.READ))

        aclService.createOrUpdatePermission(userHome, notUser, AclPermission.READ)
        assertFalse(aclService.hasPermission(userHome, notUser, AclPermission.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AclPermission.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val user = it[it.keys.single()]!!.single()
            user.permission == AclPermission.READ && user.username == notUser
        }
    }

    @Test
    fun `add and remove user`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.createOrUpdatePermission(userHome, notUser, AclPermission.WRITE)

        assertTrue(aclService.hasPermission(userHome, username, AclPermission.WRITE))
        assertTrue(aclService.hasPermission(userHome, username, AclPermission.READ))

        assertTrue(aclService.hasPermission(userHome, notUser, AclPermission.WRITE))
        assertTrue(aclService.hasPermission(userHome, notUser, AclPermission.READ))

        aclService.revokePermission(userHome, notUser)
        assertFalse(aclService.hasPermission(userHome, notUser, AclPermission.WRITE))
        assertFalse(aclService.hasPermission(userHome, notUser, AclPermission.READ))

        val list = aclService.listAcl(listOf(userHome))
        assertThatPropertyEquals(list, { it.size }, 0)
    }

    @Test
    fun `test deep file for owner`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"

        (0 until 10).map { (arrayOf(userHome) + Array(it) { "dir-$it" }).joinToString("/") }.forEach { path ->
            assertTrue(aclService.hasPermission(path, username, AclPermission.WRITE))
            assertTrue(aclService.hasPermission(path, username, AclPermission.READ))
        }
    }

    @Test
    fun `test deep file for owner and shared`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.createOrUpdatePermission(userHome, notUser, AclPermission.WRITE)
        (0 until 10).map { (arrayOf(userHome) + Array(it) { "dir-$it" }).joinToString("/") }.forEach { path ->
            assertTrue(aclService.hasPermission(path, username, AclPermission.WRITE))
            assertTrue(aclService.hasPermission(path, username, AclPermission.READ))

            assertTrue(aclService.hasPermission(path, notUser, AclPermission.WRITE))
            assertTrue(aclService.hasPermission(path, notUser, AclPermission.READ))
        }
    }

    @Test
    fun `test deep file for owner and shared - not root`() = runBlocking {
        val username = "user"
        val userHome = "/home/$username"
        val notUser = "notUser"

        aclService.createOrUpdatePermission("$userHome/dir-0/dir-1/dir-2", notUser, AclPermission.WRITE)
        (0 until 10).map { (arrayOf(userHome) + Array(it) { "dir-$it" }).joinToString("/") }.forEach { path ->
            assertTrue(aclService.hasPermission(path, username, AclPermission.WRITE))
            assertTrue(aclService.hasPermission(path, username, AclPermission.READ))
        }

        (0 until 10).map { (arrayOf(userHome) + Array(it) { "dir-$it" }).joinToString("/") }.drop(3).forEach { path ->
            assertTrue(aclService.hasPermission(path, notUser, AclPermission.WRITE))
            assertTrue(aclService.hasPermission(path, notUser, AclPermission.READ))
        }
    }

    @Test
    fun `test non-normalized path`() = runBlocking {
        assertTrue(aclService.hasPermission("/home/user/../user/././././/", "user", AclPermission.WRITE))
        assertTrue(aclService.hasPermission("/home/user/../user/././././/", "user", AclPermission.READ))
    }

    @Test
    fun `test root permissions`() = runBlocking {
        assertFalse(aclService.hasPermission("/", "user", AclPermission.READ))
        assertFalse(aclService.hasPermission("/", "user", AclPermission.WRITE))

        assertFalse(aclService.hasPermission("/home", "user", AclPermission.READ))
        assertFalse(aclService.hasPermission("/home", "user", AclPermission.WRITE))

        assertFalse(aclService.hasPermission("/workspaces", "user", AclPermission.READ))
        assertFalse(aclService.hasPermission("/workspaces", "user", AclPermission.WRITE))
    }

    @Test
    fun `test very long path`() = runBlocking {
        // Note: that this folder is not in /home/user/
        val path = "/home/user" + String(CharArray(4096) { 'a' })
        assertFalse(aclService.hasPermission(path, "user", AclPermission.WRITE))
        assertFalse(aclService.hasPermission(path, "user", AclPermission.READ))
    }

    @Test
    fun `test path with new lines`() = runBlocking {
        // Note: that this folder is not in /home/user/
        val path = "/home/user\n\n\nfoo"
        assertFalse(aclService.hasPermission(path, "user", AclPermission.WRITE))
        assertFalse(aclService.hasPermission(path, "user", AclPermission.READ))
    }
}

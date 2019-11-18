package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.services.acl.AclHibernateDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.EntityType
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.Test

class AclTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService<HibernateSession>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)
        aclService = AclService(micro.hibernateDatabase, AclHibernateDao())

        micro.hibernateDatabase.withTransaction {
            it.createNativeQuery("CREATE ALIAS IF NOT EXISTS REVERSE AS \$\$ String reverse(String s) { return new StringBuilder(s).reverse().toString(); } \$\$;").executeUpdate()
        }
    }

    @Test
    fun `empty acls`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)
        val licenseId = "1234"

        val instance = aclService.listAcl(licenseId)
        assertEquals(0, instance.size)

        assertFalse(aclService.hasPermission(licenseId, user, AccessRight.READ_WRITE))
        assertFalse(aclService.hasPermission(licenseId, user, AccessRight.READ))
    }

    @Test
    fun `revoke permission`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)
        val licenseId = "1234"

        aclService.updatePermissions(licenseId, user, AccessRight.READ)
        assertTrue(aclService.hasPermission(licenseId, user, AccessRight.READ))
        aclService.revokePermission(licenseId, user)
        assertFalse(aclService.hasPermission(licenseId, user, AccessRight.READ))
    }

    @Test
    fun `add user to acl`() = runBlocking {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        val userEntity = UserEntity("user", EntityType.USER)
        val licenseId = "1234"

        assertFalse(aclService.hasPermission(licenseId, userEntity, AccessRight.READ_WRITE))

        aclService.updatePermissions(licenseId, userEntity, AccessRight.READ_WRITE)

        assertTrue(aclService.hasPermission(licenseId, userEntity, AccessRight.READ_WRITE))
    }

    @Test
    fun `test root permissions`() = runBlocking {
        val entity = UserEntity("user", EntityType.USER)

        assertFalse(aclService.hasPermission("/", entity, AccessRight.READ))
        assertFalse(aclService.hasPermission("/", entity, AccessRight.READ_WRITE))

        assertFalse(aclService.hasPermission("/home", entity, AccessRight.READ))
        assertFalse(aclService.hasPermission("/home", entity, AccessRight.READ_WRITE))

        assertFalse(aclService.hasPermission("/workspaces", entity, AccessRight.READ))
        assertFalse(aclService.hasPermission("/workspaces", entity, AccessRight.READ_WRITE))
    }

    @Test
    fun `add user to acl several times`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)
        val notUser = UserEntity("notUser", EntityType.USER)
        val licenseId = "1234"

        repeat(10) {
            aclService.updatePermissions(licenseId, user, AccessRight.READ_WRITE)
        }

        val list = aclService.listAcl(licenseId)
        assertThatPropertyEquals(list, { it.size }, 1)
        assertThatInstance(list) {
            val user = it.single()
            user.permission == AccessRight.READ_WRITE && user.entity.id == "user"
        }
    }
}
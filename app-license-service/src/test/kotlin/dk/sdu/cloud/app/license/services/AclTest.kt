package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.api.ACLEntryRequest
import dk.sdu.cloud.app.license.api.NewServerRequest
import dk.sdu.cloud.app.license.services.acl.*
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.*
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.Test
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import kotlin.math.absoluteValue
import kotlin.random.Random

class AclTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService<HibernateSession>
    private lateinit var licenseService: AppLicenseService<HibernateSession>
    private lateinit var principal: SecurityPrincipal
    private lateinit var principal2: SecurityPrincipal

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        principal = SecurityPrincipal("user", Role.ADMIN, "user", "user", Random.nextLong().absoluteValue, "user@example.com")
        principal2 = SecurityPrincipal("user2", Role.ADMIN, "user2", "user2", Random.nextLong().absoluteValue, "user2@example.com")

        ClientMock.mockCall(UserDescriptions.lookupUsers) {
            TestCallResult.Ok(
                LookupUsersResponse(it.users.map { it to UserLookup(it, it.hashCode().toLong(), Role.USER) }.toMap())
            )
        }

        aclService = AclService(micro.hibernateDatabase, ClientMock.authenticatedClient, AclHibernateDao())
        licenseService = AppLicenseService(micro.hibernateDatabase, aclService, AppLicenseHibernateDao())

        micro.hibernateDatabase.withTransaction {
            it.createNativeQuery("CREATE ALIAS IF NOT EXISTS REVERSE AS \$\$ String reverse(String s) { return new StringBuilder(s).reverse().toString(); } \$\$;")
                .executeUpdate()
        }
    }

    @Test
    fun `empty acls`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)
        val serverId = "1234"

        val instance = aclService.listAcl(serverId)
        assertEquals(0, instance.size)

        assertFalse(aclService.hasPermission(serverId, user, ServerAccessRight.READ_WRITE))
        assertFalse(aclService.hasPermission(serverId, user, ServerAccessRight.READ))
    }

    @Test
    fun `revoke permission`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)
        val user2 = UserEntity("user2", EntityType.USER)

        val serverId = licenseService.createLicenseServer(
            NewServerRequest(
                "test",
                "example.com",
                "1234",
                null
            ),
            user
        )

        val changes = listOf(ACLEntryRequest(user2, ServerAccessRight.READ))

        aclService.updatePermissions(serverId, changes, user)
        assertTrue(aclService.hasPermission(serverId, user2, ServerAccessRight.READ))
        aclService.revokePermission(serverId, user2)
        assertFalse(aclService.hasPermission(serverId, user2, ServerAccessRight.READ))
    }

    @Test
    fun `add user to acl`() = runBlocking {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        val userEntity = UserEntity("user", EntityType.USER)
        val userEntity2 = UserEntity("user2", EntityType.USER)

        val serverId = licenseService.createLicenseServer(
            NewServerRequest(
                "test",
                "example.com",
                "1234",
                null
            ),
            userEntity
        )


        assertFalse(aclService.hasPermission(serverId, userEntity2, ServerAccessRight.READ))

        val changes = listOf(ACLEntryRequest(userEntity2, ServerAccessRight.READ))

        aclService.updatePermissions(serverId, changes, userEntity)

        assertTrue(aclService.hasPermission(serverId, userEntity2, ServerAccessRight.READ))
    }

    @Test
    fun `add user to acl several times`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)
        val user2 = UserEntity("user2", EntityType.USER)

        val serverId = licenseService.createLicenseServer(
            NewServerRequest(
                "test",
                "example.com",
                "1234",
                null
            ),
            user
        )

        val changes = listOf(ACLEntryRequest(user2, ServerAccessRight.READ))

        repeat(10) {
            aclService.updatePermissions(serverId, changes, user)
        }

        val list = aclService.listAcl(serverId)
        assertThatPropertyEquals(list, { it.size }, 2)

        assertTrue(EntityWithPermission("user", EntityType.USER, ServerAccessRight.READ_WRITE) in list)
        assertTrue(EntityWithPermission("user2", EntityType.USER, ServerAccessRight.READ) in list)
    }
}

package dk.sdu.cloud.app.license.services

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
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import io.mockk.mockk

class AclTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService<HibernateSession>
    private lateinit var licenseService: AppLicenseService<HibernateSession>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        val authClient = mockk<AuthenticatedClient>(relaxed = true)

        ClientMock.mockCall(UserDescriptions.lookupUsers) {
            TestCallResult.Ok(
                LookupUsersResponse(it.users.map { it to UserLookup(it, it.hashCode().toLong(), Role.USER) }.toMap())
            )
        }

        aclService = AclService(micro.hibernateDatabase, ClientMock.authenticatedClient, AclHibernateDao())
        licenseService = AppLicenseService(micro.hibernateDatabase, aclService, AppLicenseHibernateDao(), authClient)

        runBlocking {
            micro.hibernateDatabase.withTransaction {
                it.createNativeQuery("CREATE ALIAS IF NOT EXISTS REVERSE AS \$\$ String reverse(String s) { return new StringBuilder(s).reverse().toString(); } \$\$;")
                    .executeUpdate()
            }
        }
    }

    @Test
    fun `empty acls`() = runBlocking {
        val user = AccessEntity("user", null, null)
        val serverId = "1234"

        val instance = aclService.listAcl(serverId)
        assertEquals(0, instance.size)

        assertFalse(aclService.hasPermission(serverId, user, ServerAccessRight.READ_WRITE))
        assertFalse(aclService.hasPermission(serverId, user, ServerAccessRight.READ))
    }

    @Test
    fun `revoke permission`() = runBlocking {
        val user = AccessEntity("user", null, null)
        val user2 = AccessEntity("user2", null, null)

        val serverId = licenseService.createLicenseServer(
            NewServerRequest(
                "test",
                "example.com",
                1234,
                null
            ),
            user
        )

        val changes = listOf(AclEntryRequest(user2, ServerAccessRight.READ))

        aclService.updatePermissions(serverId, changes, user)
        assertTrue(aclService.hasPermission(serverId, user2, ServerAccessRight.READ))
        aclService.revokePermission(serverId, user2)
        assertFalse(aclService.hasPermission(serverId, user2, ServerAccessRight.READ))
    }

    @Test
    fun `add user to acl`() = runBlocking {
        val micro = initializeMicro()
        micro.install(HibernateFeature)

        val userEntity = AccessEntity("user", null, null)
        val userEntity2 = AccessEntity("user2", null, null)

        val serverId = licenseService.createLicenseServer(
            NewServerRequest(
                "test",
                "example.com",
                1234,
                null
            ),
            userEntity
        )


        assertFalse(aclService.hasPermission(serverId, userEntity2, ServerAccessRight.READ))

        val changes = listOf(AclEntryRequest(userEntity2, ServerAccessRight.READ))

        aclService.updatePermissions(serverId, changes, userEntity)

        assertTrue(aclService.hasPermission(serverId, userEntity2, ServerAccessRight.READ))
    }

    @Test
    fun `add user to acl several times`() = runBlocking {
        val user = AccessEntity("user", null, null)
        val user2 = AccessEntity("user2", null, null)

        val serverId = licenseService.createLicenseServer(
            NewServerRequest(
                "test",
                "example.com",
                1234,
                null
            ),
            user
        )

        val changes = listOf(AclEntryRequest(user2, ServerAccessRight.READ))

        repeat(10) {
            aclService.updatePermissions(serverId, changes, user)
        }

        val list = aclService.listAcl(serverId)
        assertThatPropertyEquals(list, { it.size }, 2)

        assertTrue(AccessEntityWithPermission(AccessEntity("user", null, null), ServerAccessRight.READ_WRITE) in list)
        assertTrue(AccessEntityWithPermission(AccessEntity("user2", null, null), ServerAccessRight.READ) in list)
    }
}

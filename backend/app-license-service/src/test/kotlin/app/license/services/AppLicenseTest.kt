package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.app.license.api.AccessEntity
import dk.sdu.cloud.app.license.api.NewServerRequest
import dk.sdu.cloud.app.license.api.UpdateServerRequest
import dk.sdu.cloud.app.license.services.acl.AclHibernateDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AppLicenseTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService<HibernateSession>
    private lateinit var appLicenseService: AppLicenseService<HibernateSession>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        ClientMock.mockCall(UserDescriptions.lookupUsers) {
            TestCallResult.Ok(
                LookupUsersResponse(it.users.map { it to UserLookup(it, it.hashCode().toLong(), Role.USER) }.toMap())
            )
        }

        val authClient = mockk<AuthenticatedClient>(relaxed = true)

        aclService = AclService(micro.hibernateDatabase, ClientMock.authenticatedClient, AclHibernateDao())
        appLicenseService = AppLicenseService(micro.hibernateDatabase, aclService, AppLicenseHibernateDao(), authClient)
    }

    @Test
    fun `save new license server and fetch`() = runBlocking {
        val user = AccessEntity("user", null, null)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                1234,
                 null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(TestUsers.admin, serverId, user)?.name)
    }

    @Test
    fun `save new license and update`() = runBlocking {
        val user = AccessEntity("user", null, null)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                1234,
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(TestUsers.admin, serverId, user)?.name)
        val newAddress = "new-address.com"

        appLicenseService.updateLicenseServer(
            TestUsers.admin,
            UpdateServerRequest(
                "testName",
                newAddress,
                1234,
                null,
                serverId
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(TestUsers.admin, serverId, user)?.name)
        assertEquals(newAddress, appLicenseService.getLicenseServer(TestUsers.admin, serverId, user)?.address)
    }

    @Test
    fun `save and update license - fail if unauthorized`() = runBlocking {
        val user = AccessEntity("user", null, null)
        val user2 = AccessEntity("user2", null, null)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                1234,
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(TestUsers.admin, serverId, user)?.name)
        val newAddress = "new-address.com"

        assertFails {
            appLicenseService.updateLicenseServer(
                TestUsers.user,
                UpdateServerRequest(
                    "testName",
                    newAddress,
                    1234,
                    null,
                    serverId
                ),
                user2
            )
        }

        assertFails { appLicenseService.getLicenseServer(TestUsers.user, serverId, user2) }
        assertEquals("example.com", appLicenseService.getLicenseServer(TestUsers.admin, serverId, user)?.address)
    }
}

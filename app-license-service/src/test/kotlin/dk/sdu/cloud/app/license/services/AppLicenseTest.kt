package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.NewServerRequest
import dk.sdu.cloud.app.license.api.UpdateServerRequest
import dk.sdu.cloud.app.license.services.acl.AclHibernateDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.EntityType
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.math.absoluteValue
import kotlin.random.Random
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

        aclService = AclService(micro.hibernateDatabase, ClientMock.authenticatedClient, AclHibernateDao())
        appLicenseService = AppLicenseService(micro.hibernateDatabase, aclService, AppLicenseHibernateDao())
    }

    @Test
    fun `save new license server and fetch`() = runBlocking {
        val user = UserEntity(TestUsers.admin, EntityType.USER)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                "1234",
                 null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(serverId, user)?.name)
    }

    @Test
    fun `save new license and update`() = runBlocking {
        val user = UserEntity(TestUsers.admin, EntityType.USER)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                "1234",
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(serverId, user)?.name)
        val newAddress = "new-address.com"

        appLicenseService.updateLicenseServer(
            UpdateServerRequest(
                "testName",
                newAddress,
                "1234",
                null,
                serverId
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(serverId, user)?.name)
        assertEquals(newAddress, appLicenseService.getLicenseServer(serverId, user)?.address)
    }

    @Test
    fun `save and update license - fail if unauthorized`() = runBlocking {
        val user = UserEntity(TestUsers.admin, EntityType.USER)
        val user2 = UserEntity(TestUsers.user, EntityType.USER)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                "1234",
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(serverId, user)?.name)
        val newAddress = "new-address.com"

        assertFails {
            appLicenseService.updateLicenseServer(
                UpdateServerRequest(
                    "testName",
                    newAddress,
                    "1234",
                    null,
                    serverId
                ),
                user2
            )
        }

        assertFails { appLicenseService.getLicenseServer(serverId, user2) }
        assertEquals("example.com", appLicenseService.getLicenseServer(serverId, user)?.address)
    }
}

package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.api.AddApplicationsToServerRequest
import dk.sdu.cloud.app.license.api.Application
import dk.sdu.cloud.app.license.api.NewServerRequest
import dk.sdu.cloud.app.license.api.UpdateServerRequest
import dk.sdu.cloud.app.license.services.acl.AclHibernateDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.EntityType
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class AppLicenseTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService<HibernateSession>
    private lateinit var appLicenseService: AppLicenseService<HibernateSession>

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        aclService = AclService(micro.hibernateDatabase, AclHibernateDao())
        appLicenseService = AppLicenseService(micro.hibernateDatabase, aclService, AppLicenseHibernateDao())
    }

    @Test
    fun `save new license server and fetch`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                "1234",
                 null,
                null
            ),
            user
        )

        assertEquals("testName", appLicenseService.getLicenseServer(serverId, user)?.name)
    }

    @Test
    fun `save new license and update`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                "1234",
                null,
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
        val user = UserEntity("user", EntityType.USER)
        val user2 = UserEntity("user2", EntityType.USER)

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                "1234",
                null,
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

    @Test
    fun `save multiple and list`() = runBlocking {
        val user = UserEntity("user", EntityType.USER)

        val appList1 = listOf(
            Application("app1"),
            Application("app2"),
            Application("app3")
        )

        val appList2 = listOf(
            Application("app2")
        )

        val serverId = appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName",
                "example.com",
                "1234",
                null,
                null
            ),
            user
        )

        appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName2",
                "example2.com",
                "1234",
                null,
                appList2
            ),
            user
        )

        appLicenseService.addApplicationsToServer(
            AddApplicationsToServerRequest(
                appList1,
                serverId
            ),
            user
        )

        val serverListApp1 = appLicenseService.listServers(Application("app1"), user)
        assertEquals(1, serverListApp1.size)
        assertTrue(serverListApp1.map { it.name }.contains("testName"))

        val serverListApp2 = appLicenseService.listServers(Application("app2"), user)
        assertEquals(2, serverListApp2?.size)
        assertTrue(serverListApp2.map { it.name }.contains("testName"))
        assertTrue(serverListApp2.map { it.name }.contains("testName2"))

        val serverListApp3 = appLicenseService.listServers(Application("app3"), user)
        assertEquals(1, serverListApp3.size)
        assertTrue(serverListApp3.map { it.name }.contains("testName"))

    }
}
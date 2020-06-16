package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.app.license.api.AccessEntity
import dk.sdu.cloud.app.license.api.AppLicenseServiceDescription
import dk.sdu.cloud.app.license.api.NewServerRequest
import dk.sdu.cloud.app.license.api.UpdateServerRequest
import dk.sdu.cloud.app.license.services.acl.AclAsyncDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AppLicenseTest {
    companion object {
        private lateinit var embDb: EmbeddedPostgres
        private lateinit var db: AsyncDBSessionFactory

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDb) = TestDB.from(AppLicenseServiceDescription)
            this.db = db
            this.embDb = embDb
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDb.close()
        }
    }

    private lateinit var aclService: AclService
    private lateinit var appLicenseService: AppLicenseService

    @BeforeTest
    fun initializeTest() {
        runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        """
                            TRUNCATE license_servers, permissions, tags
                        """.trimIndent()
                    )
            }
        }
        ClientMock.mockCall(UserDescriptions.lookupUsers) {
            TestCallResult.Ok(
                LookupUsersResponse(it.users.map { it to UserLookup(it, it.hashCode().toLong(), Role.USER) }.toMap())
            )
        }

        val authClient = mockk<AuthenticatedClient>(relaxed = true)

        aclService = AclService(db, ClientMock.authenticatedClient, AclAsyncDao())
        appLicenseService = AppLicenseService(db, aclService, AppLicenseAsyncDao(), authClient)
    }

    @AfterTest
    fun after() {
        runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        """
                            TRUNCATE license_servers, permissions, tags
                        """.trimIndent()
                    )
            }
        }
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

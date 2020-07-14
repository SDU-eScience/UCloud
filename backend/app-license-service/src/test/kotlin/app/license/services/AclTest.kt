package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.services.acl.*
import dk.sdu.cloud.auth.api.*
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
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.AfterTest

class AclTest {
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
    private lateinit var licenseService: AppLicenseService

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
        val authClient = mockk<AuthenticatedClient>(relaxed = true)

        ClientMock.mockCall(UserDescriptions.lookupUsers) {
            TestCallResult.Ok(
                LookupUsersResponse(it.users.map { user ->
                    user to UserLookup(user, user.hashCode().toLong(), Role.USER)
                }.toMap())
            )
        }

        aclService = AclService(db, ClientMock.authenticatedClient, AclAsyncDao())
        licenseService = AppLicenseService(db, aclService, AppLicenseAsyncDao(), authClient)

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

        println(embDb.getJdbcUrl("postgres", "postgres"))
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

        assertEquals(user.user, list.first().entity.user)
        assertEquals(ServerAccessRight.READ_WRITE, list.first().permission)

        assertEquals(user2.user, list.last().entity.user)
        assertEquals(ServerAccessRight.READ, list.last().permission)
    }
}

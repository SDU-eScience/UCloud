package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.app.license.api.AccessEntity
import dk.sdu.cloud.app.license.api.AppLicenseServiceDescription
import dk.sdu.cloud.app.license.api.DeleteServerRequest
import dk.sdu.cloud.app.license.api.NewServerRequest
import dk.sdu.cloud.app.license.api.UpdateServerRequest
import dk.sdu.cloud.app.license.services.acl.AclAsyncDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserGroupSummary
import dk.sdu.cloud.project.api.UserStatusInProject
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

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

        ClientMock.mockCallSuccess(
            ProjectMembers.userStatus,
            UserStatusResponse(
                listOf(
                    UserStatusInProject(
                        "projectID",
                        "title",
                        ProjectMember(TestUsers.user.username, ProjectRole.PI),
                        null
                    )
                ),
                listOf(
                    UserGroupSummary("projectID", "group1", TestUsers.user.username)
                )
            )
        )

        val authClient = ClientMock.authenticatedClient

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
    fun `save new license server, fetch, add tags and delete`() = runBlocking {
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

        appLicenseService.createLicenseServer(
            NewServerRequest(
                "testName2",
                "2example.com",
                4321,
                null
            ),
            AccessEntity("user2", null, null)
        )

        assertEquals("testName", appLicenseService.getLicenseServer(TestUsers.admin, serverId, user)?.name)
        val list = appLicenseService.listAllServers(TestUsers.admin)
        assertTrue(list.isNotEmpty())
        assertEquals(1234, list.first().port)

        appLicenseService.addTag("tag1", serverId)
        appLicenseService.addTag("tag2", serverId)
        val tags = appLicenseService.listTags(serverId)
        assertEquals("tag1", tags.first())
        assertEquals("tag2", tags.last())

        val listedByTags = appLicenseService.listServers(listOf("tag2"), TestUsers.user)
        assertEquals(1, listedByTags.size)
        assertEquals("testName", listedByTags.first().name)

        appLicenseService.deleteTag("tag2", serverId)

        val listedByTagsAfterDelete = appLicenseService.listServers(listOf("tag2"), TestUsers.user)
        assertTrue(listedByTagsAfterDelete.isEmpty())

        val tagsAfterDelete = appLicenseService.listTags(serverId)
        assertTrue(tagsAfterDelete.size == 1)
        assertEquals("tag1", tags.first())

        appLicenseService.deleteLicenseServer(TestUsers.user, DeleteServerRequest(serverId))

        assertEquals(0, appLicenseService.listTags(serverId).size)


        val listAfterDelete = appLicenseService.listAllServers(TestUsers.admin)
        assertEquals(1, listAfterDelete.size)
        assertEquals("testName2", listAfterDelete.first().name)
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

package dk.sdu.cloud.app.license.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.license.api.AccessEntity
import dk.sdu.cloud.app.license.api.AccessEntityWithPermission
import dk.sdu.cloud.app.license.api.AclEntryRequest
import dk.sdu.cloud.app.license.api.AddTagRequest
import dk.sdu.cloud.app.license.api.AppLicenseServiceDescription
import dk.sdu.cloud.app.license.api.DeleteTagRequest
import dk.sdu.cloud.app.license.api.LicenseServerId
import dk.sdu.cloud.app.license.api.LicenseServerWithId
import dk.sdu.cloud.app.license.api.ListLicenseServersRequest
import dk.sdu.cloud.app.license.api.ListTagsResponse
import dk.sdu.cloud.app.license.api.NewServerRequest
import dk.sdu.cloud.app.license.api.NewServerResponse
import dk.sdu.cloud.app.license.api.ServerAccessRight
import dk.sdu.cloud.app.license.api.UpdateAclRequest
import dk.sdu.cloud.app.license.api.UpdateServerRequest
import dk.sdu.cloud.app.license.services.AppLicenseAsyncDao
import dk.sdu.cloud.app.license.services.AppLicenseService
import dk.sdu.cloud.app.license.services.acl.AclAsyncDao
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserGroupSummary
import dk.sdu.cloud.project.api.UserStatusInProject
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.*
import io.ktor.http.HttpMethod
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class AppLicenseControllerTest {

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

    @BeforeTest
    fun before() {
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

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val client = ClientMock.authenticatedClient
        val aclDao = AclAsyncDao()
        val aclService = AclService(db, client, aclDao)
        val appLicenseDao = AppLicenseAsyncDao()
        val appLicenseService = AppLicenseService(db, aclService, appLicenseDao, client)

        ClientMock.mockCallSuccess(
            UserDescriptions.lookupUsers,
            LookupUsersResponse(
                mapOf(
                    TestUsers.admin.username to
                            UserLookup(
                                TestUsers.admin.username,
                                TestUsers.admin.uid,
                                TestUsers.admin.role
                            ),
                    TestUsers.user.username to
                            UserLookup(
                                TestUsers.user.username,
                                TestUsers.user.uid,
                                TestUsers.user.role
                            )
                )
            )
        )

        ClientMock.mockCallSuccess(
            ProjectMembers.userStatus,
            UserStatusResponse(
                listOf(
                    UserStatusInProject(
                        "projectId",
                        "title",
                        ProjectMember(TestUsers.user.username, ProjectRole.ADMIN),
                        null
                    )
                ),
                listOf(
                    UserGroupSummary("projectId", "group1", TestUsers.user.username)
                )
            )
        )

        listOf(AppLicenseController(appLicenseService))
    }

    @Test
    fun `test controller`() {
        withKtorTest(
            setup = setup,
            test = {
                val baseContext = "/api/app/license"
                val tagBaseContext = "/api/app/license/tag"
                val createRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "$baseContext/new",
                    user = TestUsers.admin,
                    request = NewServerRequest(
                        "testName",
                        "testAddress",
                        1234,
                        "license"
                    )
                )
                createRequest.assertSuccess()
                val serverId = defaultMapper.readValue<NewServerResponse>(createRequest.response.content!!)

                sendJson(
                    method = HttpMethod.Post,
                    path = "$baseContext/new",
                    user = TestUsers.admin,
                    request = NewServerRequest(
                        "testName2",
                        "testAddress2",
                        4321,
                        "license2"
                    )
                ).assertSuccess()


                val getRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "$baseContext",
                    user = TestUsers.admin,
                    params = mapOf("serverId" to serverId.serverId)
                )
                getRequest.assertSuccess()

                val server = defaultMapper.readValue<LicenseServerWithId>(getRequest.response.content!!)
                assertEquals(serverId.serverId, server.id)

                val listAllRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "$baseContext/listAll",
                    user = TestUsers.admin
                )
                listAllRequest.assertSuccess()
                val all = defaultMapper.readValue<List<LicenseServerId>>(listAllRequest.response.content!!)
                assertEquals(2, all.size)
                assertEquals("testName2", all.last().name)
                assertEquals("testName", all.first().name)

                val updateRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "$baseContext/update",
                    user = TestUsers.admin,
                    request = UpdateServerRequest(
                        "newName",
                        "newAddress",
                        5678,
                        "NewLicense",
                        serverId.serverId
                    )
                )
                updateRequest.assertSuccess()

                val listAllAfterUpdateRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "$baseContext/listAll",
                    user = TestUsers.admin
                )
                listAllAfterUpdateRequest.assertSuccess()
                val after = defaultMapper.readValue<List<LicenseServerId>>(listAllAfterUpdateRequest.response.content!!)
                assertEquals(2, after.size)
                assertEquals("newName", after.last().name)

                val listAcl = sendRequest(
                    method = HttpMethod.Get,
                    path = "$baseContext/listAcl",
                    user = TestUsers.admin,
                    params = mapOf("serverId" to serverId.serverId)
                )
                listAcl.assertSuccess()
                val acl = defaultMapper.readValue<List<AccessEntityWithPermission>>(listAcl.response.content!!)
                assertEquals(1, acl.size)
                assertEquals(TestUsers.admin.username, acl.first().entity.user)

                val updateAclRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "$baseContext/updateAcl",
                    user = TestUsers.admin,
                    request = UpdateAclRequest(
                        serverId.serverId,
                        listOf(
                            AclEntryRequest(
                                AccessEntity(
                                    TestUsers.user.username,
                                    null,
                                    null
                                ),
                                ServerAccessRight.READ
                            )
                        )
                    )
                )
                updateAclRequest.assertSuccess()

                val listAclAfterUpdate = sendRequest(
                    method = HttpMethod.Get,
                    path = "$baseContext/listAcl",
                    user = TestUsers.admin,
                    params = mapOf("serverId" to serverId.serverId)
                )
                listAclAfterUpdate.assertSuccess()

                val aclAfter = defaultMapper.readValue<List<AccessEntityWithPermission>>(listAclAfterUpdate.response.content!!)
                assertEquals(2, aclAfter.size)
                assertEquals(TestUsers.admin.username, aclAfter.first().entity.user)
                assertEquals(TestUsers.user.username, aclAfter.last().entity.user)
                assertEquals(ServerAccessRight.READ_WRITE, aclAfter.first().permission)
                assertEquals(ServerAccessRight.READ, aclAfter.last().permission)


                sendJson(
                    method = HttpMethod.Post,
                    path = "$tagBaseContext/add",
                    user = TestUsers.admin,
                    request = AddTagRequest(
                        serverId.serverId,
                        "tag1"
                    )
                ).assertSuccess()

                sendJson(
                    method = HttpMethod.Post,
                    path = "$tagBaseContext/add",
                    user = TestUsers.admin,
                    request = AddTagRequest(
                        serverId.serverId,
                        "tag2"
                    )
                ).assertSuccess()

                val listTagsRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "$tagBaseContext/list",
                    user = TestUsers.admin,
                    params = mapOf("serverId" to serverId.serverId)
                )
                listTagsRequest.assertSuccess()

                val tags = defaultMapper.readValue<ListTagsResponse>(listTagsRequest.response.content!!)
                assertEquals(2, tags.tags.size)

                val listRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "$baseContext/list",
                    user = TestUsers.admin,
                    request = ListLicenseServersRequest(
                        listOf("tag1")
                    )
                )
                listRequest.assertSuccess()
                val serversWithtag = defaultMapper.readValue<List<LicenseServerId>>(listRequest.response.content!!)
                assertEquals(1, serversWithtag.size)

                val deleteTagRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "$tagBaseContext/delete",
                    user = TestUsers.admin,
                    request = DeleteTagRequest(
                        serverId.serverId,
                        "tag1"
                    )
                )
                deleteTagRequest.assertSuccess()

                val listRequestAfterTagDelete = sendRequest(
                    method = HttpMethod.Get,
                    path = "$tagBaseContext/list",
                    user = TestUsers.admin,
                    params = mapOf("serverId" to serverId.serverId)
                )
                listRequestAfterTagDelete.assertSuccess()
                val tagsAfterDelete = defaultMapper.readValue<ListTagsResponse>(listRequestAfterTagDelete.response.content!!)
                assertEquals(1, tagsAfterDelete.tags.size)
            }
        )
    }
}

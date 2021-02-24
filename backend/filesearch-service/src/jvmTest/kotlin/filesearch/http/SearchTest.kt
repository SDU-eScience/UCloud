package dk.sdu.cloud.filesearch.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.filesearch.api.AdvancedSearchRequest
import dk.sdu.cloud.filesearch.api.SearchResult
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.QueryResponse
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTest {
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val micro = initializeMicro()
        val cloud = ClientMock.authenticatedClient
        listOf(SearchController(cloud))
    }

    private val file: StorageFileImpl = StorageFileImpl(
        pathOrNull = "path",
        ownerNameOrNull = "owner",
        fileTypeOrNull = FileType.FILE,
        createdAtOrNull = 12342431, modifiedAtOrNull = 12345,
        sizeOrNull = 1234,
        ownSensitivityLevelOrNull = SensitivityLevel.PRIVATE,
        sensitivityLevelOrNull = SensitivityLevel.PRIVATE
    )

    private val queryResponse = QueryResponse(
        2,
        10,
        0,
        listOf(file, file.copy(pathOrNull = "path2"))
    )
    //No controller for simple search
    @Ignore
    @Test
    fun `simple test`() {
        withKtorTest(
            setup,
            test = {
                ClientMock.mockCallSuccess(
                    QueryDescriptions.query,
                    queryResponse
                )

                ClientMock.mockCallSuccess(
                    FileDescriptions.verifyFileKnowledge,
                    VerifyFileKnowledgeResponse(
                        listOf(true, true)
                    )
                )

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/file-search",
                    user = TestUsers.user,
                    params = mapOf("query" to "path"),
                    configure = { addHeader("X-No-Load", "true") }
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<Page<SearchResult>>(request.response.content!!)

                assertEquals(2, response.itemsInTotal)
                assertEquals(1, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals("path", response.items.first().path)
                assertEquals("path2", response.items.last().path)
            }
        )
    }
    //No controller for simple search
    @Ignore
    @Test
    fun `simple test - verify returns a single false`() {
        withKtorTest(
            setup,
            test = {
                ClientMock.mockCallSuccess(
                    QueryDescriptions.query,
                    queryResponse
                )

                ClientMock.mockCallSuccess(
                    FileDescriptions.verifyFileKnowledge,
                    VerifyFileKnowledgeResponse(
                        listOf(true, false)
                    )
                )

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/file-search",
                    user = TestUsers.user,
                    params = mapOf("query" to "path"),
                    configure = { addHeader("X-No-Load", "true") }
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<Page<SearchResult>>(request.response.content!!)

                assertEquals(2, response.itemsInTotal)
                assertEquals(1, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals("path", response.items.first().path)
            }
        )
    }
    //No controller for simple search
    @Ignore
    @Test
    fun `simple test - verify throws ex`() {
        withKtorTest(
            setup,
            test = {
                ClientMock.mockCallSuccess(
                    QueryDescriptions.query,
                    queryResponse
                )

                ClientMock.mockCallError(
                    FileDescriptions.verifyFileKnowledge,
                    CommonErrorMessage("fail"),
                    HttpStatusCode.InternalServerError
                )

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/file-search",
                    user = TestUsers.user,
                    params = mapOf("query" to "path"),
                    configure = { addHeader("X-No-Load", "true") }
                )
                request.assertStatus(HttpStatusCode.InternalServerError)
            }
        )
    }

    private val req = AdvancedSearchRequest(
        "name",
        null,
        null,
        false,
        10,
        0
    )

    @Test
    fun `advanced test - empty query`() {
        withKtorTest(
            setup
            ,
            test = {
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/file-search/advanced",
                    user = TestUsers.user,
                    request = req.copy(fileName = null)
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<Page<SearchResult>>(request.response.content!!)

                assertEquals(0, response.itemsInTotal)
                assertEquals(0, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertTrue(response.items.isEmpty())
            }
        )
    }

    @Test
    fun `advanced test - only file name`() {
        withKtorTest(
            setup
            ,
            test = {
                ClientMock.mockCallSuccess(
                    QueryDescriptions.query,
                    queryResponse
                )

                ClientMock.mockCallSuccess(
                    FileDescriptions.verifyFileKnowledge,
                    VerifyFileKnowledgeResponse(
                        listOf(true, true)
                    )
                )

                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/file-search/advanced",
                    user = TestUsers.user,
                    request = req.copy()
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<Page<SearchResult>>(request.response.content!!)

                assertEquals(2, response.itemsInTotal)
                assertEquals(1, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals("path", response.items.first().path)
                assertEquals("path2", response.items.last().path)
            }
        )
    }

    @Test
    fun `advanced test`() {
        withKtorTest(
            setup
            ,
            test = {
                ClientMock.mockCallSuccess(
                    QueryDescriptions.query,
                    queryResponse
                )

                ClientMock.mockCallSuccess(
                    FileDescriptions.verifyFileKnowledge,
                    VerifyFileKnowledgeResponse(
                        listOf(true, true)
                    )
                )

                ClientMock.mockCallSuccess(
                    ProjectMembers.userStatus,
                    UserStatusResponse(
                        listOf(
                            UserStatusInProject(
                                "projectID",
                                "title",
                                ProjectMember(TestUsers.user.username, ProjectRole.ADMIN),
                                null
                            )
                        ),
                        listOf(
                            UserGroupSummary(
                                "projectID",
                                "groupname",
                                TestUsers.user.username
                            )
                        )
                    )
                )

                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/file-search/advanced",
                    user = TestUsers.user,
                    request = req
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<Page<SearchResult>>(request.response.content!!)

                assertEquals(2, response.itemsInTotal)
                assertEquals(1, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals("path", response.items.first().path)
                assertEquals("path2", response.items.last().path)
            }
        )
    }

    @Test
    fun `advanced test - verify returns a single false`() {
        withKtorTest(
            setup,
            test = {
                ClientMock.mockCallSuccess(
                    QueryDescriptions.query,
                    queryResponse
                )

                ClientMock.mockCallSuccess(
                    FileDescriptions.verifyFileKnowledge,
                    VerifyFileKnowledgeResponse(
                        listOf(true, false)
                    )
                )
                ClientMock.mockCallSuccess(
                    ProjectMembers.userStatus,
                    UserStatusResponse(
                        listOf(
                            UserStatusInProject(
                                "projectID",
                                "title",
                                ProjectMember(TestUsers.user.username, ProjectRole.ADMIN),
                                null
                            )
                        ),
                        listOf(
                            UserGroupSummary(
                                "projectID",
                                "groupname",
                                TestUsers.user.username
                            )
                        )
                    )
                )
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/file-search/advanced",
                    user = TestUsers.user,
                    request = req
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<Page<SearchResult>>(request.response.content!!)

                assertEquals(2, response.itemsInTotal)
                assertEquals(1, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals("path", response.items.first().path)
            }
        )
    }

    @Test
    fun `advanced test - verify throws ex`() {
        withKtorTest(
            setup,
            test = {
                ClientMock.mockCallSuccess(
                    QueryDescriptions.query,
                    queryResponse
                )

                ClientMock.mockCallError(
                    FileDescriptions.verifyFileKnowledge,
                    CommonErrorMessage("fail"),
                    HttpStatusCode.InternalServerError
                )
                ClientMock.mockCallSuccess(
                    ProjectMembers.userStatus,
                    UserStatusResponse(
                        listOf(
                            UserStatusInProject(
                                "projectID",
                                "title",
                                ProjectMember(TestUsers.user.username, ProjectRole.ADMIN),
                                null
                            )
                        ),
                        listOf(
                            UserGroupSummary(
                                "projectID",
                                "groupname",
                                TestUsers.user.username
                            )
                        )
                    )
                )
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/file-search/advanced",
                    user = TestUsers.user,
                    request = req
                )
                request.assertStatus(HttpStatusCode.InternalServerError)
            }
        )
    }
}

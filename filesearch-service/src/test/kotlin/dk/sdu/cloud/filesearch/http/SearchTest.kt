package dk.sdu.cloud.filesearch.http

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.readValues
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.api.VerifyFileKnowledgeResponse
import dk.sdu.cloud.filesearch.api.AdvancedSearchRequest
import dk.sdu.cloud.filesearch.api.SearchResult
import dk.sdu.cloud.filesearch.api.TimestampQuery
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.QueryResponse
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertFailure
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class SearchTest{

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val micro = initializeMicro()
        val cloud = micro.authenticatedCloud
        listOf(SearchController())
    }

    private val file = EventMaterializedStorageFile(
        "1",
        "path",
        "owner",
        FileType.FILE,
        Timestamps(12342431,12345,12345),
        1234,
        FileChecksum(
            "SHA1",
            "checksum"
        ),
        false,
        null,
        null,
        emptySet(),
        SensitivityLevel.PRIVATE
    )

    private val queryResponse = QueryResponse(
        2,
        10,
        0,
        listOf(file, file.copy(id = "2"))
    )

    @Test
    fun `simple test`() {
        withKtorTest(
            setup
            ,
            test = {
                CloudMock.mockCallSuccess(
                    QueryDescriptions,
                    { QueryDescriptions.query},
                    queryResponse
                )

                CloudMock.mockCallSuccess(
                    FileDescriptions,
                    { FileDescriptions.verifyFileKnowledge },
                    VerifyFileKnowledgeResponse(
                        listOf(true, true)
                    )
                )

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/file-search",
                    user = TestUsers.user,
                    params = mapOf("query" to "path")
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<Page<SearchResult>>(request.response.content!!)

                assertEquals(2, response.itemsInTotal)
                assertEquals(1, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals("1", response.items.first().fileId)
                assertEquals("2", response.items.last().fileId)
            }
        )
    }

    @Test
    fun `simple test - verify returns a single false`() {
        withKtorTest(
            setup
            ,
            test = {
                CloudMock.mockCallSuccess(
                    QueryDescriptions,
                    { QueryDescriptions.query},
                    queryResponse
                )

                CloudMock.mockCallSuccess(
                    FileDescriptions,
                    { FileDescriptions.verifyFileKnowledge },
                    VerifyFileKnowledgeResponse(
                        listOf(true, false)
                    )
                )

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/file-search",
                    user = TestUsers.user,
                    params = mapOf("query" to "path")
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<Page<SearchResult>>(request.response.content!!)

                assertEquals(2, response.itemsInTotal)
                assertEquals(1, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals("1", response.items.first().fileId)
            }
        )
    }

    @Test
    fun `simple test - verify throws ex`() {
        withKtorTest(
            setup
            ,
            test = {
                CloudMock.mockCallSuccess(
                    QueryDescriptions,
                    { QueryDescriptions.query},
                    queryResponse
                )

                CloudMock.mockCallError(
                    FileDescriptions,
                    { FileDescriptions.verifyFileKnowledge },
                    CommonErrorMessage("fail"),
                    HttpStatusCode.InternalServerError
                )

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/file-search",
                    user = TestUsers.user,
                    params = mapOf("query" to "path")
                )
                request.assertStatus(HttpStatusCode.InternalServerError)
            }
        )
    }

    private val req = AdvancedSearchRequest(
        "name",
        null,
        null,
        null,
        TimestampQuery(123, 123456789),
        null,
        null,
        10,
        0
    )

    @Test
    fun `advanced test`() {
        withKtorTest(
            setup
            ,
            test = {
                CloudMock.mockCallSuccess(
                    QueryDescriptions,
                    { QueryDescriptions.query},
                    queryResponse
                )

                CloudMock.mockCallSuccess(
                    FileDescriptions,
                    { FileDescriptions.verifyFileKnowledge },
                    VerifyFileKnowledgeResponse(
                        listOf(true, true)
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
                assertEquals("1", response.items.first().fileId)
                assertEquals("2", response.items.last().fileId)
            }
        )
    }

    @Test
    fun `advanced test - verify returns a single false`() {
        withKtorTest(
            setup
            ,
            test = {
                CloudMock.mockCallSuccess(
                    QueryDescriptions,
                    { QueryDescriptions.query},
                    queryResponse
                )

                CloudMock.mockCallSuccess(
                    FileDescriptions,
                    { FileDescriptions.verifyFileKnowledge },
                    VerifyFileKnowledgeResponse(
                        listOf(true, false)
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
                assertEquals("1", response.items.first().fileId)
            }
        )
    }

    @Test
    fun `advanced test - verify throws ex`() {
        withKtorTest(
            setup
            ,
            test = {
                CloudMock.mockCallSuccess(
                    QueryDescriptions,
                    { QueryDescriptions.query},
                    queryResponse
                )

                CloudMock.mockCallError(
                    FileDescriptions,
                    { FileDescriptions.verifyFileKnowledge },
                    CommonErrorMessage("fail"),
                    HttpStatusCode.InternalServerError
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

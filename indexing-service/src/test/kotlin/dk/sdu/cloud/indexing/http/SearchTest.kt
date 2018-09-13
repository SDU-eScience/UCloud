package dk.sdu.cloud.indexing.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.indexing.services.IndexQueryService
import dk.sdu.cloud.service.*
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.indexing.utils.withAuthMock
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(io.ktor.http.HttpHeaders.Authorization, "Bearer $username/$role")
}

private fun Application.configureBaseServer(vararg controllers: Controller) {
    installDefaultFeatures(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        requireJobId = true
    )

    routing {
        configureControllers(*controllers)
    }
}

private fun Application.configureSearchServer(indexQueryService: IndexQueryService) {
    configureBaseServer(SearchController(indexQueryService))
}

class SearchTest {

    private val eventFile = EventMaterializedStorageFile(
        "id",
        "path/to/object",
        "owner Of File",
        FileType.FILE,
        Timestamps(1234567890, 12345678, 123456789),
        82828,
        FileChecksum("sha1", "Checksum"),
        false,
        null,
        null,
        setOf("Annotation"),
        SensitivityLevel.SENSITIVE
    )

    @Test
    fun `Simple search test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val indexQueryService = mockk<IndexQueryService>()
                    every { indexQueryService.simpleQuery(any(), any(), any()) } answers {
                        val result = Page(1, 10, 0, listOf(eventFile))
                        result
                    }

                    configureSearchServer(indexQueryService)

                },

                test = {
                    objectMockk(FileDescriptions).use {
                        coEvery { FileDescriptions.verifyFileKnowledge.call(any(), any()) } answers {
                            RESTResponse.Ok(
                                mockk(relaxed = true),
                                VerifyFileKnowledgeResponse(listOf(true))
                            )
                        }

                        val response =
                            handleRequest(HttpMethod.Get, "/api/file-search?query=testSearch&itemsPerPage=10&page=0") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val mapper = jacksonObjectMapper()
                        val obj = mapper.readTree(response.content)
                        assertEquals("1", obj["itemsInTotal"].toString())
                        assertTrue(obj["items"].toString().contains("\"path\":\"path/to/object\""))
                    }
                }
            )
        }
    }

    @Test
    fun `Simple search test - Query results length and verify result length not equal `() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val indexQueryService = mockk<IndexQueryService>()
                    every { indexQueryService.simpleQuery(any(), any(), any()) } answers {
                        val result = Page(1, 10, 0, listOf(eventFile))
                        result
                    }

                    configureSearchServer(indexQueryService)

                },

                test = {
                    objectMockk(FileDescriptions).use {
                        coEvery { FileDescriptions.verifyFileKnowledge.call(any(), any()) } answers {
                            RESTResponse.Ok(
                                mockk(relaxed = true),
                                VerifyFileKnowledgeResponse(listOf(true, false))
                            )
                        }

                        val response =
                            handleRequest(HttpMethod.Get, "/api/file-search?query=testSearch&itemsPerPage=10&page=0") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.InternalServerError, response.status())
                    }
                }
            )
        }
    }

    @Test
    fun `Simple search test - Unauthorized`() {
        withTestApplication(
            moduleFunction = {
                val indexQueryService = mockk<IndexQueryService>()
                configureSearchServer(indexQueryService)
            },

            test = {
                val response =
                    handleRequest(HttpMethod.Get, "/api/file-search?query=testSearch&itemsPerPage=10&page=0") {
                        addHeader("Job-Id", UUID.randomUUID().toString())
                    }.response

                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        )
    }

    @Test
    fun `Advanced search test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val indexQueryService = mockk<IndexQueryService>()

                    every {
                        indexQueryService.advancedQuery(
                            any(), any(), any(), any(), any(),
                            any(), any(), any(), any(), any()
                        )
                    } answers {
                        val result = Page(1, 10, 0, listOf(eventFile))
                        result
                    }

                    configureSearchServer(indexQueryService)
                },

                test = {
                    objectMockk(FileDescriptions).use {
                        coEvery { FileDescriptions.verifyFileKnowledge.call(any(), any()) } answers {
                            RESTResponse.Ok(
                                mockk(relaxed = true),
                                VerifyFileKnowledgeResponse(listOf(true))
                            )
                        }

                        val response =
                            handleRequest(HttpMethod.Post, "/api/file-search/advanced") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                                setBody(
                                    """
                                {
                                "fileName" : "filename",
                                "annotations" : ["Annotation"],
                                "owner" : "owners Name",
                                "modifiedAt" : {
                                    "after" : 1234567,
                                    "before" : 12345678
                                },
                                "createdAt" : {
                                    "after" : 1234567,
                                    "before" : 12345678
                                },
                                "extensions" : ["extension"],
                                "fileTypes" : ["FILE"],
                                "page" : 0,
                                "itemsPerPage" : 10,

                                            },
                                "sensitivity" : "CONFIDENTIAL"
                                }
                            """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val mapper = jacksonObjectMapper()
                        val obj = mapper.readTree(response.content)
                        assertEquals("1", obj["itemsInTotal"].toString())
                        assertTrue(obj["items"].toString().contains("\"path\":\"path/to/object\""))                    }
                }
            )
        }
    }
}
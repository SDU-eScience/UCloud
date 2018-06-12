package dk.sdu.cloud.metadata.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.metadata.services.*
import dk.sdu.cloud.metadata.utils.withAuthMock
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

private fun Application.configureMetadataServer(
    elasticMetadataService: ElasticMetadataService,
    projectService: ProjectService = ProjectService(ProjectSQLDao())
) {
    configureMetadataServer(elasticMetadataService, elasticMetadataService, elasticMetadataService, projectService)
}

private fun Application.configureMetadataServer(
    metadataCommandService: ElasticMetadataService,
    metadataQueryService: MetadataQueryService,
    metadataAdvancedQueryService: MetadataAdvancedQueryService,
    projectService: ProjectService = ProjectService(ProjectSQLDao())
) {
    Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver"
    )

    transaction {
        SchemaUtils.create(Projects)
    }

    configureBaseServer(
        MetadataController(
            metadataCommandService,
            metadataQueryService,
            metadataAdvancedQueryService,
            projectService
        )
    )
}

class MetadataTest {

    private val source = """
                                {
                                    "sduCloudRoot" : "",
                                    "title" : "I got a title",
                                    "files" : [
                                        {
                                        "id" : "2",
                                        "type" : "${FileType.FILE}",
                                        "path" : "home"
                                        }
                                    ],
                                    "creators" : [
                                        {
                                        "name" : "I. A. M. User"
                                        }
                                    ],
                                    "description" : "Here is my new description",
                                    "id" : "1"
                                }
                                """.trimIndent()

    @Test
    fun `make update of metadata test`() {
        objectMockk(FileDescriptions).use {
            val user = "user1"
            withAuthMock {
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
                val projectService: ProjectService = mockk(relaxed = true)
                val elasticService = ElasticMetadataService(
                    elasticClient = elasticClient,
                    projectService = projectService
                )

                withTestApplication(
                    moduleFunction = {
                        configureMetadataServer(elasticService)
                        every { projectService.findById(any()) } returns Project(
                            "1",
                            "",
                            user,
                            "description is here"
                        )

                        every { elasticClient.get(any()) } answers {
                            val getResponse = mockk<GetResponse>()
                            every { getResponse.isExists } returns true
                            every { getResponse.sourceAsBytes } returns source.toByteArray()
                            getResponse
                        }
                    },
                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/api/metadata") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(user)
                                setBody(
                                    """
                                {
                                    "id" : "1",
                                    "title" : "A project title",
                                    "description" : "This description is nice"
                                }
                                """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        verify {
                            elasticClient.index(
                                match {
                                    it.index() == "project_metadata" && it.sourceAsMap().containsValue("A project title")
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    @Test
    fun `find by ID test`() {
        objectMockk(FileDescriptions).use {
            val user = "user1"
            withAuthMock {
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
                val projectService: ProjectService = mockk(relaxed = true)
                val elasticService = ElasticMetadataService(
                    elasticClient = elasticClient,
                    projectService = projectService
                )

                withTestApplication(
                    moduleFunction = {
                        configureMetadataServer(elasticService)

                        every { elasticClient.get(any()) } answers {
                            val getResponse = mockk<GetResponse>()
                            every { getResponse.isExists } returns true
                            every { getResponse.sourceAsBytes } returns source.toByteArray()
                            getResponse
                        }
                    },
                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/metadata/1") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(user)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        verify {
                            elasticClient.get(
                                match{
                                    println(it)
                                    it.index() == "project_metadata" && it.id() == "1"
                                }
                            )
                        }
                    }
                )
            }
        }
    }



    @Test
    fun `find by ID - Nothing found - test`() {
        objectMockk(FileDescriptions).use {
            val user = "user1"
            withAuthMock {
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
                val projectService: ProjectService = mockk(relaxed = true)
                val elasticService = ElasticMetadataService(
                    elasticClient = elasticClient,
                    projectService = projectService
                )

                withTestApplication(
                    moduleFunction = {
                        configureMetadataServer(elasticService)
                    },
                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/metadata/id=1") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(user)
                            }.response

                        assertEquals(HttpStatusCode.NotFound, response.status())

                    }
                )
            }
        }
    }

    @Test
    fun `find by path test`() {
        objectMockk(FileDescriptions).use {
            val user = "user1"
            withAuthMock {
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
                val projectService: ProjectService = mockk(relaxed = true)
                val elasticService = ElasticMetadataService(
                    elasticClient = elasticClient,
                    projectService = projectService
                )

                withTestApplication(
                    moduleFunction = {
                        configureMetadataServer(elasticService, projectService)

                        every { projectService.findByFSRoot(any()) } returns Project(
                                "2",
                                "/home/",
                                user,
                                "This is my project"
                            )

                        every { elasticClient.get(any()) } answers {
                            val getResponse = mockk<GetResponse>()
                            every { getResponse.isExists } returns true
                            every { getResponse.sourceAsBytes } returns source.toByteArray()
                            getResponse
                        }
                    },
                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/metadata/by-path?path=/home/") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(user)
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        verify { elasticClient.get(
                            match {
                                it.index() == "project_metadata" && it.id() == "2"
                            }
                        ) }

                    }
                )
            }
        }
    }


    @Test
    fun `find By Path - Not existing project - test`() {
        objectMockk(FileDescriptions).use {
            val user = "user1"
            withAuthMock {
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
                val projectService: ProjectService = mockk(relaxed = true)
                val elasticService = ElasticMetadataService(
                    elasticClient = elasticClient,
                    projectService = projectService
                )

                withTestApplication(
                    moduleFunction = {
                        configureMetadataServer(elasticService)

                        every { elasticClient.get(any()) } answers {
                            val getResponse = mockk<GetResponse>()
                            every { getResponse.isExists } returns true
                            every { getResponse.sourceAsBytes } returns source.toByteArray()
                            getResponse
                        }
                    },
                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/metadata/by-path?path=/home/") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(user)
                            }.response

                        assertEquals(HttpStatusCode.NotFound, response.status())

                    }
                )
            }
        }
    }

    @Test
    fun `simple query test`() {
        objectMockk(FileDescriptions).use {
            val user = "user1"
            withAuthMock {
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
                val projectService: ProjectService = mockk(relaxed = true)
                val elasticService = ElasticMetadataService(
                    elasticClient = elasticClient,
                    projectService = projectService
                )

                withTestApplication(
                    moduleFunction = {
                        configureMetadataServer(elasticService)

                        every { elasticClient.search(any()) } answers {
                            val searchResponse = mockk<SearchResponse>()
                            val hit = Array<SearchHit>(22, {i -> SearchHit(i)})
                            every { searchResponse.hits } returns SearchHits(hit, 22, 0.513f)
                            searchResponse

                        }
                    },
                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/metadata/search?query=wunderbar&itemsPerPage=10&page=0") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(user)
                            }.response
                        //Throws 22 exceptions due to the hits not containing any info.
                        assertEquals(HttpStatusCode.OK, response.status())

                        val mapper = jacksonObjectMapper()
                        val obj = mapper.readTree(response.content)

                        assertEquals("22", obj["itemsInTotal"].toString())
                        assertEquals("10", obj["itemsPerPage"].toString())
                        assertEquals("2", obj["pagesInTotal"].toString())
                        assertEquals("0", obj["pageNumber"].toString())

                    }
                )
            }
        }
    }


}
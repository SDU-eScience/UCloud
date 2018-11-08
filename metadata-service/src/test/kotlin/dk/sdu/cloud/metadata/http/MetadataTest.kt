package dk.sdu.cloud.metadata.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.metadata.api.Creator
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.services.ElasticMetadataService
import dk.sdu.cloud.metadata.services.MetadataAdvancedQueryService
import dk.sdu.cloud.metadata.services.MetadataQueryService
import dk.sdu.cloud.metadata.services.Project
import dk.sdu.cloud.metadata.services.ProjectException
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.apache.http.HttpHost
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.junit.Ignore
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

private fun configureMetadataServer(
    elasticMetadataService: ElasticMetadataService,
    projectService: ProjectService<*>
): List<Controller> {
    return configureMetadataServer(
        elasticMetadataService,
        elasticMetadataService,
        elasticMetadataService,
        projectService
    )
}

private fun configureMetadataServer(
    metadataCommandService: ElasticMetadataService,
    metadataQueryService: MetadataQueryService,
    metadataAdvancedQueryService: MetadataAdvancedQueryService,
    projectService: ProjectService<*>
): List<Controller> {
    return listOf(
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
            "sduCloudRootId" : "",
            "title" : "I got a title",
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
        val user = "user1"
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
        )

        withKtorTest(
            setup = {
                every { projectService.findById(any()) } returns Project(
                    1,
                    "",
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

                configureMetadataServer(elasticService, projectService)
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/api/metadata") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
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
            }
        )
    }

    @Test
    fun `find by ID test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
        )

        withKtorTest(
            setup = {
                every { elasticClient.get(any()) } answers {
                    val getResponse = mockk<GetResponse>()
                    every { getResponse.isExists } returns true
                    every { getResponse.sourceAsBytes } returns source.toByteArray()
                    getResponse
                }

                configureMetadataServer(elasticService, projectService)
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/metadata/1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    verify {
                        elasticClient.get(
                            match {
                                println(it)
                                it.index() == "project_metadata" && it.id() == "1"
                            }
                        )
                    }
                }
            }
        )
    }


    @Test
    fun `find by ID - Nothing found - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
        )

        withKtorTest(
            setup = {
                configureMetadataServer(elasticService, projectService)
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/metadata/1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        )
    }

    @Test
    fun `find by path test`() {
        val user = "user1"
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
        )

        withKtorTest(
            setup = {
                every { projectService.findByFSRoot(any()) } returns Project(
                    2,
                    "/home/",
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

                configureMetadataServer(elasticService, projectService)
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/metadata/by-path?path=/home/") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    verify {
                        elasticClient.get(
                            match {
                                it.index() == "project_metadata" && it.id() == "2"
                            }
                        )
                    }

                }
            }
        )
    }

    @Test
    fun `find by path - Not in Elastic - test`() {
        val user = "user1"
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
        )

        withKtorTest(
            setup = {
                every { projectService.findByFSRoot(any()) } returns Project(
                    2,
                    "/home/",
                    "/home/",
                    user,
                    "This is my project"
                )

                every { elasticClient.get(any()) } answers {
                    val getResponse = mockk<GetResponse>()
                    every { getResponse.isExists } returns false
                    getResponse
                }

                configureMetadataServer(elasticService, projectService)
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/metadata/by-path?path=/home/") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())

                    verify {
                        elasticClient.get(
                            match {
                                it.index() == "project_metadata" && it.id() == "2"
                            }
                        )
                    }

                }
            }
        )
    }

    @Test
    fun `find By Path - Not existing project - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
        )

        every { projectService.findByFSRoot(any()) } throws ProjectException.NotFound()

        withKtorTest(
            setup = {
                every { elasticClient.get(any()) } answers {
                    val getResponse = mockk<GetResponse>()
                    every { getResponse.isExists } returns true
                    every { getResponse.sourceAsBytes } returns source.toByteArray()
                    getResponse
                }
                configureMetadataServer(elasticService, projectService)
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/metadata/by-path?path=/home/") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        )
    }

    @Test
    fun `simple query test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
        )

        withKtorTest(
            setup = {
                every { elasticClient.search(any()) } answers { _ ->
                    val searchResponse = mockk<SearchResponse>()
                    val hit = Array(22) { SearchHit(it) }
                    every { searchResponse.hits } returns SearchHits(hit, 22, 0.513f)
                    searchResponse

                }

                configureMetadataServer(elasticService, projectService)
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(
                            HttpMethod.Get,
                            "/api/metadata/search?query=wunderbar&itemsPerPage=10&page=0"
                        ) {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response
                    //Throws 22 exceptions due to the hits not containing any info.
                    assertEquals(HttpStatusCode.OK, response.status())

                    val mapper = jacksonObjectMapper()
                    val obj = mapper.readTree(response.content)

                    assertEquals("22", obj["itemsInTotal"].toString())
                    assertEquals("10", obj["itemsPerPage"].toString())
                    assertEquals("3", obj["pagesInTotal"].toString())
                    assertEquals("0", obj["pageNumber"].toString())

                }
            }
        )
    }

    //Used for live test - works only on machines that have elastic installed and running on localhost:9200
    private val metaProject = ProjectMetadata(
        "CloudRoot",
        "CloudRootId",
        "title",
        listOf(Creator("nameOfCreator")),
        "This is a descrption",
        null,
        "1",
        1234567,
        "open",
        listOf("Mutiple", "keywords"),
        "These are simple notes for the proejct"
    )

    private val metaProject2 = ProjectMetadata(
        "CloudRoot2",
        "CloudRootId2",
        "anthtermetaProject",
        listOf(Creator("nameOfCreator")),
        "This is a alternative description",
        null,
        "2",
        1234567,
        "open",
        listOf("Mutiple", "keywords", "Unique"),
        "These are simple notes for the proejct that the other project does not have"
    )

    @Ignore
    @Test
    fun `query test live`() {
        val elasticClient = RestHighLevelClient(RestClient.builder(HttpHost("localhost", 9200, "http")))
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
        )

        withKtorTest(
            setup = {
                elasticService.initializeElasticSearch()

                elasticService.create(metaProject)
                elasticService.create(metaProject2)

                Thread.sleep(2000)
                configureMetadataServer(elasticService, projectService)
            },
            test = {
                with(engine) {
                    val response =
                        handleRequest(
                            HttpMethod.Get,
                            "/api/metadata/search?query=Uniq&itemsPerPage=10&page=0"
                        ) {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val mapper = jacksonObjectMapper()
                    val obj = mapper.readTree(response.content)

                    println(response.content)
                    /* assertEquals("22", obj["itemsInTotal"].toString())

                 assertEquals("10", obj["itemsPerPage"].toString())
                 assertEquals("2", obj["pagesInTotal"].toString())
                 assertEquals("0", obj["pageNumber"].toString())
*/
                }
            }
        )
    }
}

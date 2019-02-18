package dk.sdu.cloud.metadata.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.api.ProjectMetadataEditRequest
import dk.sdu.cloud.metadata.services.ElasticMetadataService
import dk.sdu.cloud.metadata.services.MetadataAdvancedQueryService
import dk.sdu.cloud.metadata.services.MetadataQueryService
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.ViewProjectResponse
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertEquals

private fun configureMetadataServer(
    elasticMetadataService: ElasticMetadataService
): List<Controller> {
    return configureMetadataServer(
        elasticMetadataService,
        elasticMetadataService,
        elasticMetadataService
    )
}

private fun configureMetadataServer(
    metadataCommandService: ElasticMetadataService,
    metadataQueryService: MetadataQueryService,
    metadataAdvancedQueryService: MetadataAdvancedQueryService
): List<Controller> {
    return listOf(
        MetadataController(
            metadataCommandService,
            metadataQueryService,
            metadataAdvancedQueryService
        )
    )
}

class MetadataTest {

    private val source = """
        {
            "title" : "I got a title",
            "description" : "Here is my new description",
            "projectId" : "ThisIsTheID"
        }
        """.trimIndent()

    @Test
    fun `make update of metadata test`() {
        val micro = initializeMicro()
        val cloud = ClientMock.authenticatedClient
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            cloud = cloud
        )

        withKtorTest(
            setup = {

                every { elasticClient.get(any()) } answers {
                    val getResponse = mockk<GetResponse>()
                    every { getResponse.isExists } returns true
                    every { getResponse.sourceAsBytes } returns source.toByteArray()
                    getResponse
                }

                configureMetadataServer(elasticService)
            },
            test = {

                ClientMock.mockCallSuccess(
                    ProjectDescriptions.view,
                    ViewProjectResponse(
                        "ThisIsTheID",
                        "ThisIsTheID",
                        listOf(ProjectMember(TestUsers.user.username, ProjectRole.PI))
                    )
                )

                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/api/metadata",
                            user = TestUsers.user,
                            request = ProjectMetadataEditRequest(
                                id = "ThisIsTheID",
                                title = "A project title",
                                description = "This description is nice"
                            )
                        )

                    request.assertSuccess()

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
        val micro = initializeMicro()
        val cloud = ClientMock.authenticatedClient
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            cloud = cloud
        )

        withKtorTest(
            setup = {
                every { elasticClient.get(any()) } answers {
                    val getResponse = mockk<GetResponse>()
                    every { getResponse.isExists } returns true
                    every { getResponse.sourceAsBytes } returns source.toByteArray()
                    getResponse
                }

                configureMetadataServer(elasticService)
            },
            test = {
                run {
                    ClientMock.mockCallSuccess(
                        ProjectDescriptions.view,
                        ViewProjectResponse(
                            "hello",
                            "hello",
                            listOf(ProjectMember("user1", ProjectRole.PI))
                        )
                    )

                    val request =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/metadata/hello",
                            user = TestUsers.user
                        )
                    println(request.response.content)
                    request.assertSuccess()

                    verify {
                        elasticClient.get(
                            match {
                                println(it)
                                it.index() == "project_metadata" && it.id() == "hello"
                            }
                        )
                    }
                }
            }
        )
    }


    @Test
    fun `find by ID - Nothing found - test`() {
        val micro = initializeMicro()
        val cloud = ClientMock.authenticatedClient
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            cloud = cloud
        )

        withKtorTest(
            setup = {
                configureMetadataServer(elasticService)
            },
            test = {
                run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/metadata/ThisIsAnID",
                            user = TestUsers.user
                        )
                    request.assertStatus(HttpStatusCode.NotFound)
                }
            }
        )
    }


    @Test
    fun `simple query test`() {
        val micro = initializeMicro()
        val cloud = ClientMock.authenticatedClient
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            cloud = cloud
        )

        withKtorTest(
            setup = {
                every { elasticClient.search(any()) } answers { _ ->
                    val searchResponse = mockk<SearchResponse>()
                    val hit = Array(22) { SearchHit(it) }
                    every { searchResponse.hits } returns SearchHits(hit, 22, 0.513f)
                    searchResponse

                }

                configureMetadataServer(elasticService)
            },
            test = {
                run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/metadata/search",
                            user = TestUsers.user,
                            params = mapOf("query" to "wunderbar", "itemsPerPage" to 10, "page" to 0)
                        )
                    //Throws 22 exceptions due to the hits not containing any info.
                    request.assertSuccess()

                    val mapper = jacksonObjectMapper()
                    val obj = mapper.readTree(request.response.content)

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
        "title",
        "This is a descrption",
        null,
        "Project1",
        listOf("Mutiple", "keywords"),
        "These are simple notes for the project"
    )

    private val metaProject2 = ProjectMetadata(
        "anothermetaProject",
        "This is a alternative description",
        null,
        "Project2",
        listOf("Mutiple", "keywords", "Unique"),
        "These are simple notes for the project that the other project does not have"
    )

    @Ignore
    @Test
    fun `query test live`() {
        val micro = initializeMicro()
        val cloud = ClientMock.authenticatedClient
        val elasticClient = RestHighLevelClient(RestClient.builder(HttpHost("localhost", 9200, "http")))
        val elasticService = ElasticMetadataService(
            elasticClient = elasticClient,
            cloud = cloud
        )

        withKtorTest(
            setup = {
                elasticService.initializeElasticSearch()

                elasticService.create(metaProject)
                elasticService.create(metaProject2)

                Thread.sleep(2000)
                configureMetadataServer(elasticService)
            },
            test = {
                run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/metadata/search",
                            user = TestUsers.user,
                            params = mapOf("query" to "Uniq", "itemsPerPage" to 10, "page" to 0)
                        )

                    request.assertSuccess()

                    println(request.response.content)

                }
            }
        )
    }
}

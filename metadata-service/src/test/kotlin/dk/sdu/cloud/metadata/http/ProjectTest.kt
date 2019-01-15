package dk.sdu.cloud.metadata.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.metadata.api.CreateProjectFromFormRequest
import dk.sdu.cloud.metadata.api.CreateProjectFromFormResponse
import dk.sdu.cloud.metadata.api.ProjectMetadataWithRightsInfo
import dk.sdu.cloud.metadata.services.ElasticMetadataService
import dk.sdu.cloud.metadata.services.MetadataAdvancedQueryService
import dk.sdu.cloud.metadata.services.MetadataQueryService
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.project.api.CreateProjectResponse
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.authenticatedCloud
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
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Test
import kotlin.test.assertEquals

private fun configureProjectServer(
    elasticMetadataService: ElasticMetadataService,
    projectService: ProjectService
): List<Controller> {
    return configureProjectServer(
        projectService,
        elasticMetadataService,
        elasticMetadataService,
        elasticMetadataService
    )
}

private fun configureProjectServer(
    projectService: ProjectService,
    elasticMetadataService: ElasticMetadataService,
    metadataQueryService: MetadataQueryService,
    metadataAdvancedQueryService: MetadataAdvancedQueryService
): List<Controller> {
    return listOf(
        ProjectsController(projectService, elasticMetadataService),
        MetadataController(
            elasticMetadataService,
            metadataQueryService,
            metadataAdvancedQueryService
        ))
}

private val source = """
        {
            "title" : "I got a title",
            "description" : "Here is my new description",
            "projectId" : "Hej"
        }
        """.trimIndent()

class ProjectTest {

    private fun createMockSucces() {
        CloudMock.mockCallSuccess(
            ProjectDescriptions,
            { ProjectDescriptions.create },
            CreateProjectResponse("Project1")
        )
    }

    @Test
    fun `Create project and get the metadata test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)

        withKtorTest(
            setup = {
                val micro = initializeMicro()
                val cloud = micro.authenticatedCloud
                configureProjectServer(
                    projectService = ProjectService(cloud),
                    elasticMetadataService = ElasticMetadataService(
                        elasticClient,
                        cloud
                    )
                )
            },

            test = {

                createMockSucces()

                run {

                    every { elasticClient.get(any()) } answers {
                        val getResponse = mockk<GetResponse>()
                        every { getResponse.isExists } returns false
                        getResponse
                    }

                    val createRequest =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/api/projects/form",
                            user = TestUsers.user,
                            request = CreateProjectFromFormRequest(
                                title = "Project1",
                                description = "This is a description"
                            )
                        )

                    createRequest.assertSuccess()

                    val createResults = defaultMapper.readValue<CreateProjectFromFormResponse>(createRequest.response.content!!)
                    val id = createResults.id

                     every { elasticClient.get(any()) } answers {
                        val getResponse = mockk<GetResponse>()
                        every { getResponse.isExists } returns true
                        every { getResponse.sourceAsBytes } returns source.toByteArray()
                        getResponse
                    }
                    val findRequest =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/metadata/$id",
                            user = TestUsers.user
                        )

                    println(findRequest)
                    findRequest.assertSuccess()


                    val findResult = defaultMapper.readValue<ProjectMetadataWithRightsInfo>(findRequest.response.content!!)
                    assertEquals("This is a description", findResult.metadata.description)
                }
            }
        )
    }

    @Test
    fun `create project - bad metadata`() {
        withKtorTest(
            setup = {
                val micro = initializeMicro()
                val cloud = micro.authenticatedCloud
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
                configureProjectServer(
                    projectService = ProjectService(cloud),
                    elasticMetadataService = ElasticMetadataService(
                        elasticClient,
                        cloud
                    )
                )
            },

            test = {
                run {
                    val request =
                        sendJson(
                            method = HttpMethod.Post,
                            path = "/api/projects/form",
                            user = TestUsers.user,
                            request = CreateProjectFromFormRequest(
                                title = "title of the project",
                                description = null
                            )
                        )
                    request.assertStatus(HttpStatusCode.BadRequest)
                }
            }
        )
    }


    @Test
    fun `get metadata - not existing - test`() {
        withKtorTest(
            setup = {
                val micro = initializeMicro()
                val cloud = micro.authenticatedCloud
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)

                every { elasticClient.get(any()) } answers {
                    val getResponse = mockk<GetResponse>()
                    every { getResponse.isExists } returns false
                    getResponse
                }

                configureProjectServer(
                    projectService = ProjectService(cloud),
                    elasticMetadataService = ElasticMetadataService(
                        elasticClient,
                        cloud
                    )
                )
            },
            test = {
               run {
                    val request =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/metadata/notAnID",
                            user = TestUsers.user
                        )
                   request.assertStatus(HttpStatusCode.NotFound)
                }
            }
        )
    }
}

package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.ViewProjectResponse
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Test

class ElasticMetadataTest {

    private val projectMeta = ProjectMetadata(
        "I got a title",
        "Here is my description",
        "Abstyles",
        "ProjectId"
    )

    private val source = """
        {
            "title" : "I got a title",
            "description" : "Here is my new description",
            "projectId" : "ProjectID"
        }
        """.trimIndent()

    private fun initService(
        elasticClient: RestHighLevelClient
    ): ElasticMetadataService {
        val micro = initializeMicro()
        return ElasticMetadataService(
            elasticClient = elasticClient,
            cloud = micro.authenticatedCloud
        )
    }

    @Test
    fun `create test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns false
            getResponse
        }

        elasticService.create(projectMeta)

        verify {
            elasticClient.index(
                match {
                    it.index() == "project_metadata" && it.id() == "ProjectId"
                }
            )
        }
    }

    @Test(expected = MetadataException.Duplicate::class)
    fun `create - already existing - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns true
            every { getResponse.sourceAsBytes } returns source.toByteArray()
            getResponse
        }

        elasticService.create(projectMeta)

    }

    @Test
    fun `delete test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)
        runBlocking {
            CloudMock.mockCallSuccess(
                ProjectDescriptions,
                { ProjectDescriptions.view },
                ViewProjectResponse(
                    "ProjectID",
                    "Title of project",
                    listOf(ProjectMember(TestUsers.user.username, ProjectRole.PI))
                )
            )


            elasticService.delete(TestUsers.user.username, "ProjectId")

            verify {
                elasticClient.delete(
                    match {
                        it.index() == "project_metadata" && it.id() == "ProjectId"
                    }
                )
            }
        }
    }

    @Test(expected = RPCException::class)
    fun `delete - project not found -test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        CloudMock.mockCallError(
            ProjectDescriptions,
            { ProjectDescriptions.view },
            CommonErrorMessage("Not found"),
            HttpStatusCode.NotFound
        )

        runBlocking {
            elasticService.delete(TestUsers.user.username, "ProjectId")
        }
    }

    @Test(expected = MetadataException.NotAllowed::class)
    fun `delete - not allowed -test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        CloudMock.mockCallSuccess(
            ProjectDescriptions,
            { ProjectDescriptions.view },
            ViewProjectResponse(
                "ProjectID",
                "Title of project",
                listOf(ProjectMember(TestUsers.user.username, ProjectRole.USER))
            )
        )

        runBlocking {
            elasticService.delete("notUser", "ProjectId")
        }
    }
    @Test
    fun `initialize test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.indices().delete(any(), any()) } answers {
            val deleteIndexResponse = mockk<DeleteIndexResponse>()
            deleteIndexResponse
        }

        elasticService.initializeElasticSearch()

        verifyOrder {
            elasticClient.indices().delete(any())
            elasticClient.indices().create(any())
        }
    }
}

package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.api.Creator
import dk.sdu.cloud.metadata.api.ProjectMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Test

class ElasticMetadataTest {
    private val dummycreators = List(10) { i -> Creator(i.toString()) }

    private val projectMeta = ProjectMetadata(
        "",
        "",
        "I got a title",
        dummycreators,
        "Here is my description",
        "Abstyles",
        "1"
    )

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

    private fun initService(
        elasticClient: RestHighLevelClient,
        projectService: ProjectService<*> = mockk(relaxed = true)
    ): ElasticMetadataService {
        return ElasticMetadataService(
            elasticClient = elasticClient,
            projectService = projectService
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
                    it.index() == "project_metadata" && it.id() == "1"
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
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = initService(elasticClient, projectService)

        every { projectService.findById(any()) } answers {
            Project(1, "", "", "user", "Description")
        }

        elasticService.delete("user", 1)

        verify {
            elasticClient.delete(
                match {
                    it.index() == "project_metadata" && it.id() == "1"
                }
            )
        }
    }

    @Test(expected = MetadataException.NotFound::class)
    fun `delete - id not found -test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = initService(elasticClient, projectService)

        every { projectService.findById(any()) } answers {
            null
        }

        elasticService.delete("user", 1)
    }

    @Test(expected = MetadataException.NotAllowed::class)
    fun `delete - not allowed -test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService<*> = mockk(relaxed = true)
        val elasticService = initService(elasticClient, projectService)

        every { projectService.findById(any()) } answers {
            Project(1, "", "", "user", "Description")
        }

        elasticService.delete("notUser", 1)
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
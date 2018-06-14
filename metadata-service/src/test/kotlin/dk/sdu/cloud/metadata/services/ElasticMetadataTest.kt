package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.api.Creator
import dk.sdu.cloud.metadata.api.FileDescriptionForMetadata
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.storage.api.FileType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.RestHighLevelClient
import org.jetbrains.exposed.sql.checkExcessiveIndices
import org.junit.Test

class ElasticMetadataTest {

    private val dummyfiles = List<FileDescriptionForMetadata>(10,
        {i -> FileDescriptionForMetadata(i.toString(),FileType.FILE, "home") })

    private val dummycreators = List<Creator>(10,
        {i -> Creator(i.toString())})

    private val projectMeta = ProjectMetadata(
        "",
        "I got a title",
        dummyfiles,
        dummycreators,
        "Here is my description",
        "Abstyles",
        "1"
    )

    private val source = """
        {
            "sduCloudRoot" : "",
            "title" : "I got a title",
            "files" : [
                {
                "id" : "2",
                "type" : "${FileType.FILE}",
                "path" : "home"
                },
                {
                "id" : "3",
                "type" : "${FileType.FILE}",
                "path" : ""
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

    private fun initService(elasticClient : RestHighLevelClient,
                            projectService: ProjectService = mockk(relaxed = true)): ElasticMetadataService {
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

        verify { elasticClient.index(
            match {
                it.index() == "project_metadata" && it.id() == "1"
            }
        )
        }
    }

    @Test (expected = MetadataException.Duplicate::class)
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
    fun `add files test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns true
            every { getResponse.sourceAsBytes } returns source.toByteArray()
            getResponse
        }

        elasticService.addFiles("1", dummyfiles.toSet())

        verify {
            elasticClient.index(
                match {
                    it.index() == "project_metadata" && it.id() == "1"
                }
            )
        }

    }

    @Test (expected = MetadataException.NotFound::class)
    fun `add files - id do not exist - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns false
            getResponse
        }

        elasticService.addFiles("1", dummyfiles.toSet())

    }

    private val pathList = List<String>(1, {i -> "home"})


    @Test
    fun `remove files test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns true
            every { getResponse.sourceAsBytes } returns source.toByteArray()
            getResponse
        }

        elasticService.removeFilesById("1", pathList.toSet())

        verify { elasticClient.index(
            match {
                it.index() == "project_metadata" && it.id() == "1"
            }
        )
        }

    }

    @Test
    fun `remove files - file is root - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns true
            every { getResponse.sourceAsBytes } returns source.toByteArray()
            getResponse
        }
        val list = listOf<String>("3")

        elasticService.removeFilesById("1", list.toSet())

        verify { elasticClient.delete(
            match{
                it.index() == "project_metadata" && it.id() == "1"
            }
        ) }

    }

    @Test (expected = MetadataException.NotFound::class)
    fun `remove files - id do not exist - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns false
            getResponse
        }
        elasticService.removeFilesById("1", pathList.toSet() )

    }

    @Test
    fun `remove ALL files test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns true
            every { getResponse.sourceAsBytes } returns source.toByteArray()
            getResponse
        }
        elasticService.removeAllFiles("1")

        verifyOrder {
            elasticClient.get(
                match {
                    it.index() == "project_metadata" && it.id() == "1"
                }
            )
            elasticClient.index(
                match {
                    it.index() == "project_metadata"  && it.id() == "1"
                }
            )
        }

    }

    @Test
    fun `update path of file test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns true
            every { getResponse.sourceAsBytes } returns source.toByteArray()
            getResponse
        }
        elasticService.updatePathOfFile("1", "2", "new/path")

        verifyOrder {
            elasticClient.get(
                match {
                    it.index() == "project_metadata" && it.id() == "1"
                }
            )
            elasticClient.index(
                match {
                    it.index() == "project_metadata" && it.id() == "1"
                }
            )
        }
    }

    @Test
    fun `update path of file - new root - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns true
            every { getResponse.sourceAsBytes } returns source.toByteArray()
            getResponse
        }
        elasticService.updatePathOfFile("1", "3", "new/path")

        verifyOrder {
            elasticClient.get(
                match {
                    it.index() == "project_metadata" && it.id() == "1"
                }
            )

            elasticClient.index(
                match {
                    it.index() == "project_metadata" && it.id() == "1"
                }
            )
        }
    }

    @Test (expected = MetadataException.NotFound::class)
    fun `update path of file - index not found - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns false
            getResponse
        }

        elasticService.updatePathOfFile("1", "2", "home/new/path")

        verify{
            elasticClient.get(
                match { it.index() == "project_metadata" && it.id() == "1" }
            )
        }

    }

    @Test (expected = MetadataException.NotFound::class)
    fun `update path of file - file not found - test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val elasticService = initService(elasticClient)

        every { elasticClient.get(any()) } answers {
            val getResponse = mockk<GetResponse>()
            every { getResponse.isExists } returns true
            every { getResponse.sourceAsBytes } returns source.toByteArray()
            getResponse
        }

        elasticService.updatePathOfFile("1", "4", "home/new/path")

        verify{
            elasticClient.get(
                match { it.index() == "project_metadata" && it.id() == "1" }
            )
        }
    }

    @Test
    fun `delete test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService = mockk(relaxed = true)
        val elasticService = initService(elasticClient, projectService)

        every { projectService.findById(any())} answers {
            Project("1", "", "user", "Description")
        }

        elasticService.delete("user", "1")

        verify {
            elasticClient.delete(
                match{
                    it.index() == "project_metadata" && it.id() == "1"
                }
            )
        }
    }

    @Test (expected = MetadataException.NotFound::class)
    fun `delete - id not found -test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService = mockk(relaxed = true)
        val elasticService = initService(elasticClient, projectService)

        every { projectService.findById(any())} answers {
            null
        }

        elasticService.delete("user", "1")
    }

    @Test (expected = MetadataException.NotAllowed::class)
    fun `delete - not allowed -test`() {
        val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
        val projectService: ProjectService = mockk(relaxed = true)
        val elasticService = initService(elasticClient, projectService)

        every { projectService.findById(any())} answers {
            Project("1", "", "user", "Description")
        }

        elasticService.delete("notUser", "1")
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
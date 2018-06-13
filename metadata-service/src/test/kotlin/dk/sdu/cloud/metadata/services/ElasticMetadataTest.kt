package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.api.Creator
import dk.sdu.cloud.metadata.api.FileDescriptionForMetadata
import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.storage.api.FileType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.RestHighLevelClient
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

    private fun initService(elasticClient : RestHighLevelClient): ElasticMetadataService {
        val projectService: ProjectService = mockk(relaxed = true)
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
        val rootPathList = List<String>(1, {i -> ""})

        elasticService.removeFilesById("1", rootPathList.toSet())

        verify { elasticClient.delete(
            match{
                it.id() == "1"
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
}
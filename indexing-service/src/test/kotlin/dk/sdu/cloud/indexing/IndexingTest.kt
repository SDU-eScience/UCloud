package dk.sdu.cloud.indexing

import dk.sdu.cloud.indexing.services.ElasticIndexingService
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.StorageEvent
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexingTest {
    @Test
    fun `test indexing of file with annotation`() {
        withFreshIndex {
            val events = ArrayList<StorageEvent>().apply {
                file("a", FileType.FILE, setOf("P"))
            }

            val response = indexingService.bulkHandleEvent(events)
            assertEquals(0, response.failures.size)

            val fileInIndex = queryService.findFileByIdOrNull("a")
            assertTrue(fileInIndex != null)
            val annotations = fileInIndex!!.annotations
            assertEquals(1, annotations.size)
            assertEquals("P", annotations.first())
        }
    }

    @Test
    fun `test index and find`() {
        withFreshIndex {
            val events = ArrayList<StorageEvent>().apply {
                file("a", FileType.FILE)
            }

            val response = indexingService.bulkHandleEvent(events)
            assertEquals(0, response.failures.size)

            val fileInIndex = queryService.findFileByIdOrNull("a")
            assertTrue(fileInIndex != null)
            fileInIndex!!
            assertEquals("a", fileInIndex.path)
            assertEquals(FileType.FILE, fileInIndex.fileType)
        }
    }

    @Test
    fun `test not found by id`() {
        withFreshIndex {
            val fileInIndex = queryService.findFileByIdOrNull("a")
            assertTrue(fileInIndex == null)
        }
    }

    private data class TestContext(
        val elastic: RestHighLevelClient,
        val indexingService: ElasticIndexingService,
        val queryService: ElasticQueryService
    )

    private fun withFreshIndex(consumer: TestContext.() -> Unit) {
        val elastic = RestHighLevelClient(RestClient.builder(HttpHost("localhost", 9200, "http")))
        val indexingService = ElasticIndexingService(elastic)
        val queryService = ElasticQueryService(elastic)
        TestContext(elastic, indexingService, queryService).apply { indexingService.migrate() }.consumer()
    }

}
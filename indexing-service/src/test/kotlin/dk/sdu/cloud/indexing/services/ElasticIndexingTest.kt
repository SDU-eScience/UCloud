package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageFile
import io.mockk.every
import io.mockk.mockk
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.CreateIndexResponse
import org.elasticsearch.rest.RestStatus
import org.junit.Test

class ElasticIndexingTest {
    private val file = StorageFile(
        FileType.FILE,
        "path/to/",
        123456789,
        223456789,
        "owner",
        88888,
        emptyList(),
        SensitivityLevel.PRIVATE,
        false,
        emptySet(),
        "id",
        "owner",
        SensitivityLevel.PRIVATE
    )

    private val eventCreatedOrRefreshed = StorageEvent.CreatedOrRefreshed(
        file,
        10000L
    )

    private val eventDeleted = StorageEvent.Deleted(
        file,
        10000L
    )

    private val eventMoved = StorageEvent.Moved(
        "Old/path/to",
        file,
        10000L
    )

    private val eventSensitivity = StorageEvent.SensitivityUpdated(
        file.copy(ownSensitivityLevelOrNull = SensitivityLevel.CONFIDENTIAL),
        10000L
    )

    private val eventInvalid = StorageEvent.Invalidated(
        "path/to/",
        123456789
    )

    @Test
    fun `test migration`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)
        every { rest.indices().create(any<CreateIndexRequest>(), any()) } answers {
            val response = mockk<CreateIndexResponse>()
            every { response.isAcknowledged } returns true
            response
        }
        elastic.migrate()
    }

    @Test(expected = RuntimeException::class)
    fun `test migration - failure`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)
        every { rest.indices().create(CreateIndexRequest(any()), any()) } answers {
            val response = mockk<CreateIndexResponse>()
            every { response.isAcknowledged } returns false
            response
        }
        elastic.migrate()
    }

    @Test(expected = ElasticsearchStatusException::class)
    fun `test migration - exception in delete`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)
        every { rest.indices().delete(any(), any()) } answers {
            throw ElasticsearchStatusException("Something went wrong", RestStatus.BAD_REQUEST)
        }
        elastic.migrate()
    }

    @Test
    fun `create or modified Test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any(), any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.handleEvent(eventCreatedOrRefreshed)
    }

    @Test
    fun `Delete event test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.delete(any(), any()) } answers {
            val response = DeleteResponse()
            response
        }

        elastic.handleEvent(eventDeleted)
    }

    @Test
    fun `Moved test`() {

        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any(), any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.handleEvent(eventMoved)
    }

    @Test
    fun `Sensitivity update test`() {

        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any(), any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.handleEvent(eventSensitivity)
    }

    @Test
    fun `Invalidated test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any(), any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.handleEvent(eventInvalid)
    }

    @Test
    fun `Bulk test`() {
        val events = listOf(eventCreatedOrRefreshed, eventSensitivity, eventMoved)
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any(), any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.bulkHandleEvent(events)
    }

    @Test
    fun `Bulk test - error`() {
        val events = listOf(eventCreatedOrRefreshed, eventDeleted)
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.bulk(any(), any()) } answers {
            val response = mockk<BulkResponse>()
            every { response.items } answers {
                val a = Array(1) { i ->
                    BulkItemResponse(
                        i, mockk(),
                        BulkItemResponse.Failure("index", "doc", "2", IllegalArgumentException())
                    )
                }
                println(a[0])
                a
            }
            response
        }

        every { rest.update(any(), any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.bulkHandleEvent(events)
    }
}

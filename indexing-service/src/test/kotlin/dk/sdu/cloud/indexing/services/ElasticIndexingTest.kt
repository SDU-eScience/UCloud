package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.Timestamps
import io.mockk.every
import io.mockk.mockk
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.rest.RestStatus
import org.junit.Test

class ElasticIndexingTest {

    private val eventCreatedOrRefreshed = StorageEvent.CreatedOrRefreshed(
        "id",
        "path/to/",
        "owner",
        123456789,
        FileType.FILE,
        Timestamps(1234567890, 123456789, 223456789),
        88888,
        FileChecksum("sha1", "0987654efghjkllv6mnhydsjfjdashkl"),
        false,
        null,
        null,
        setOf("A"),
        SensitivityLevel.PRIVATE
    )

    private val eventDeleted = StorageEvent.Deleted(
        "id",
        "path/to/",
        "owner",
        123456789
    )

    private val eventMoved = StorageEvent.Moved(
        "id",
        "path/to/",
        "owner",
        123456789,
        "Old/path/to"
    )

    private val eventSensitivity = StorageEvent.SensitivityUpdated(
        "id",
        "path/to/",
        "owner",
        123456789,
        SensitivityLevel.CONFIDENTIAL
    )

    private val eventAnnotation = StorageEvent.AnnotationsUpdated(
        "id",
        "path/to/",
        "owner",
        123456789,
        setOf("K")
    )

    private val eventInvalid = StorageEvent.Invalidated(
        "id",
        "path/to/",
        "owner",
        123456789
    )

    @Test
    fun `test migration`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)
        every { rest.indices().create(any()) } answers {
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
        every { rest.indices().create(any()) } answers {
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
        every { rest.indices().delete(any()) } answers {
            throw ElasticsearchStatusException("Something went wrong", RestStatus.BAD_REQUEST)
        }
        elastic.migrate()
    }

    @Test
    fun `create or modified Test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.handleEvent(eventCreatedOrRefreshed)
    }

    @Test
    fun `Delete event test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.delete(any()) } answers {
            val response = DeleteResponse()
            response
        }

        elastic.handleEvent(eventDeleted)
    }

    @Test
    fun `Moved test`() {

        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.handleEvent(eventMoved)
    }

    @Test
    fun `Sensitivity update test`() {

        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.handleEvent(eventSensitivity)
    }

    @Test
    fun `Annotation update test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.handleEvent(eventAnnotation)
    }

    @Test
    fun `Invalidated test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.update(any()) } answers {
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

        every { rest.update(any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.bulkHandleEvent(events)
    }

    @Test
    fun `Bulk test - error`() {
        val events = listOf(eventCreatedOrRefreshed, eventAnnotation, eventDeleted)
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticIndexingService(rest)

        every { rest.bulk(any()) } answers {
            val response = mockk<BulkResponse>()
            every { response.items } answers {
                val a = Array(1) { i ->
                    BulkItemResponse(
                        i, mockk<DocWriteRequest.OpType>(),
                        BulkItemResponse.Failure("index", "doc", "2", IllegalArgumentException())
                    )
                }
                println(a[0])
                a
            }
            response
        }

        every { rest.update(any()) } answers {
            val response = UpdateResponse()
            response
        }

        elastic.bulkHandleEvent(events)
    }
}

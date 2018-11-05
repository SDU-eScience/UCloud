package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.service.RPCException
import io.mockk.every
import io.mockk.mockk
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.junit.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElasticQueryTest {
    // TODO Conver this stuff

    val sourceString = """
        {
          "id" : "id",
          "path" : "path/to",
          "fileName" : "filename",
          "owner" : "owner",
          "fileDepth" : "0",
          "fileType" : "FILE",
          "fileTimestamps" : {
            "accessed" : 123456789,
            "created" : 123456,
            "modified" : 1234567
          },
          "size" : 92829,
          "checksum" : {
            "algorithm" : "sha1",
            "checksum" : "checksumValue"
          },
          "linkTarget" : null,
          "linkTargetId" : null,
          "annotations" : [ "A" ],
          "sensitivity" : "CONFIDENTIAL",
          "fileIsLink" : false
        }
        """.trimIndent()

    @Test
    fun `Find Query Test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticQueryService(rest)
        every { rest.get(any()) } answers {
            val response = mockk<GetResponse>()
            every { response.isExists } returns true
            every { response.sourceAsString } returns sourceString
            response
        }
        val result = elastic.findFileByIdOrNull("1")
        assertTrue(result.toString().contains("checksumValue"))


    }

    @Test
    fun `Find Query Test - does not exist`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticQueryService(rest)
        every { rest.get(any()) } answers {
            val response = mockk<GetResponse>()
            every { response.isExists } returns false
            response
        }
        assertNull(elastic.findFileByIdOrNull("1"))
    }

    /*
    @Test
    fun `Simple Query Test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticQueryService(rest)
        every { rest.search(any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = SearchHits(Array(20) { _ -> SearchHit(2) }, 20, 1.9f)
                hits
            }
            response
        }
        assertEquals(
            20,
            elastic.simpleQuery(listOf("path", "root"), "owner", NormalizedPaginationRequest(10, 0)).itemsInTotal
        )
    }
    */

    /*
    @Test
    fun `Advanced Query Test`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticQueryService(rest)
        every { rest.search(any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = SearchHits(Array(20) { _ -> SearchHit(2) }, 20, 1.9f)
                hits
            }
            response
        }

        assertEquals(
            20,
            elastic.advancedQuery(
                listOf("path", "root"),
                "name",
                "owner",
                listOf("extensions"),
                listOf(FileType.FILE, FileType.DIRECTORY),
                TimestampQuery(123456, 1234567890),
                TimestampQuery(1234567, 1234567890),
                listOf(SensitivityLevel.CONFIDENTIAL, SensitivityLevel.OPEN_ACCESS),
                listOf("A"),
                NormalizedPaginationRequest(20, 0)
            ).itemsInTotal
        )
    }
    */

    /*
    @Test
    fun `Advanced Query Test - missing search Criteria`() {
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        val elastic = ElasticQueryService(rest)
        every { rest.search(any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = SearchHits(Array(20) { _ -> SearchHit(2) }, 20, 1.9f)
                hits
            }
            response
        }

        val result = elastic.advancedQuery(
            roots = listOf("path", "root"),
            name = null,
            owner = null,
            extensions = listOf(),
            fileTypes = listOf(),
            createdAt = null,
            modifiedAt = null,
            sensitivity = listOf(),
            annotations = listOf(),
            paging = NormalizedPaginationRequest(20, 0)
        )

        assertEquals(0, result.itemsInTotal)
    }
    */

    //TODO Does not return correctly but does give CC. Error with response.hits
    @Test
    fun `Reverse lookup batch Test`() {
        val client = mockk<RestHighLevelClient>()

        every { client.search(any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                val hits = SearchHits(Array(20) { _ -> SearchHit(2) }, 20, 1.9f)
                hits
            }
            response
        }

        val reverseLookupService = ElasticQueryService(client)
        val lookupList = List(20) { i -> i.toString() }
        val result = reverseLookupService.reverseLookupBatch(lookupList)

        println(result)
    }

    @Test(expected = RPCException::class)
    fun `Reverse lookup batch Test - To many ids`() {
        val client = mockk<RestHighLevelClient>()

        every { client.search(any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                SearchHits(arrayOf(SearchHit(1)), 1, 0.552f)
            }
            response
        }

        val reverseLookupService = ElasticQueryService(client)
        val lookupList = List(110) { i -> i.toString() }
        val result = reverseLookupService.reverseLookupBatch(lookupList)

        println(result)
    }

    @Test(expected = RPCException::class)
    fun `Reverse lookup Test - not found`() {
        val client = mockk<RestHighLevelClient>()

        every { client.search(any()) } answers {
            val response = mockk<SearchResponse>()
            every { response.hits } answers {
                SearchHits(arrayOf(SearchHit(1), SearchHit(2)), 2, 0.552f)
            }
            response
        }

        val reverseLookupService = ElasticQueryService(client)
        reverseLookupService.reverseLookup("1")
    }
}

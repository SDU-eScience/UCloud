package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.services.ActivityEventElasticDao
import dk.sdu.cloud.activity.services.ActivityEventFilter
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.services.FileLookupService
import dk.sdu.cloud.activity.util.favoriteEvent
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.apache.lucene.search.TotalHits
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class ActivityEventDaoTest {
    @Test
    fun `Find By Path test`() {
        val elasticClient = mockk<RestHighLevelClient>()
        val elasticDao = ActivityEventElasticDao(elasticClient)
        val path = "path/to/file"

        every { elasticClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>(relaxed = true)
            response
        }
        elasticDao.findByFilePath(NormalizedPaginationRequest(10, 0), path)


    }

    @Test
    fun `Find events`() {
        val elasticClient = mockk<RestHighLevelClient>()
        val elasticDao = ActivityEventElasticDao(elasticClient)

        every { elasticClient.search(any(), any()) } answers {
            val response = mockk<SearchResponse>(relaxed = true)
            response
        }

        elasticDao.findUserEvents(250, ActivityEventFilter(user = "user"))
    }
}

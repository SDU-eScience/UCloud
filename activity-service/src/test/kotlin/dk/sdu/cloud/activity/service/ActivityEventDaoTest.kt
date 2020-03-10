package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.services.ActivityEventElasticDao
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
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class ActivityEventDaoTest {
    lateinit var micro: Micro

/*
    @BeforeTest
    fun setupTest() {
        micro = initializeMicro()
    }

    @Test
    fun `Find by user Test`() {
        val client = mockk<RestHighLevelClient>()
        runBlocking {
            val dao = ActivityEventElasticDao(client)
            val results = dao.findByUser(
                NormalizedPaginationRequest(10, 0),
                TestUsers.user.username
            )

            assertEquals(2, results.itemsInTotal)
            assertEquals(favoriteEvent, results.items.last())
        }
    }

    @Test
    fun `Find By Path test`() {}

    @Test
    fun `Find events`() {

    }*/
}

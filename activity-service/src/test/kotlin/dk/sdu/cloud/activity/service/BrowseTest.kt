package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.services.HibernateActivityEventDao
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedScrollRequest
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test

class BrowseTest {
    val user = "user"
    lateinit var micro: Micro
    lateinit var service: ActivityService<HibernateSession>

    @BeforeTest
    fun setupTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)
        val dao = HibernateActivityEventDao()
        service = ActivityService(micro.hibernateDatabase, dao, mockk(relaxed = true))
    }

    @Test
    fun `test large batch job - same op`() {
        val timestamp = System.currentTimeMillis()
        val batchSize = 1000

        service.insertBatch(
            List(batchSize) { ActivityEvent.Updated(user, timestamp, "1", "/home/$user/file.txt") }
        )

        val collapseThreshold = 20
        val groups = service.browseForUser(NormalizedScrollRequest(), user, collapseThreshold)
        assertThatPropertyEquals(groups, { it.items.size }, 1)
        assertThatPropertyEquals(groups.items.first(), { it.items.size }, collapseThreshold)
        assertThatPropertyEquals(
            groups.items.first(),
            { it.numberOfHiddenResults },
            (batchSize - collapseThreshold).toLong()
        )
    }

    @Test
    fun `test large batch - repeating pattern`() {
        val timestamp = System.currentTimeMillis()
        val pattern = listOf(
            ActivityEvent.Updated(user, timestamp, "1", "/home/$user/file.txt"),
            ActivityEvent.Download(user, timestamp, "1", "/home/$user/file.txt"),
            ActivityEvent.Inspected(user, timestamp, "1", "/home/$user/file.txt")
        )
        val repeat = 200
        val batchSize = repeat * pattern.size
        service.insertBatch(List(repeat) { pattern }.flatten())

        val collapseThreshold = 20
        val groups = service.browseForUser(NormalizedScrollRequest(scrollSize = 100), user, collapseThreshold)
        assertThatPropertyEquals(groups, { it.items.size }, 3)
        assertThatPropertyEquals(groups.items.first(), { it.items.size }, collapseThreshold)
        assertThatPropertyEquals(
            groups,
            { it.nextOffset },
            batchSize
        )
    }
}

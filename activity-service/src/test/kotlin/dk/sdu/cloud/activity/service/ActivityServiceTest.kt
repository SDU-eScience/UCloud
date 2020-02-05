package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.services.FileLookupService
import dk.sdu.cloud.activity.services.HibernateActivityEventDao
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.NormalizedScrollRequest
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityServiceTest {
    val user = "user"
    val path = "/home/$user/file.txt"
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
    fun `test large batch job - same op`() = runBlocking {
        val timestamp = System.currentTimeMillis()
        val batchSize = 1000

        service.insertBatch(
            List(batchSize) { ActivityEvent.Updated(user, timestamp, "1", path) }
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
        Unit
    }

    @Test
    fun `insert find delete expired`() {
        val numberOfDaysInPast = 180L
        val earlyExpireDaysInPast = 190L
        val timestamp = System.currentTimeMillis() - (earlyExpireDaysInPast * 24 * 60 * 60 * 1000L)

        val db = micro.hibernateDatabase
        val mockedFileLookup = mockk<FileLookupService>()
        val activityService = ActivityService(db, HibernateActivityEventDao(), mockedFileLookup)

        coEvery { mockedFileLookup.lookupFile(any(), any(), any(), any()) } answers {
            StorageFile(
                FileType.FILE,
                path,
                12345,
                123456,
                user,
                123,
                null,
                SensitivityLevel.PRIVATE,
                emptySet(),
                "1",
                user,
                SensitivityLevel.PRIVATE
            )
        }
        runBlocking {
            val batchSize = 1000
            activityService.insertBatch(
                List(batchSize) { ActivityEvent.Updated(user, timestamp, "1", path) }
            )
            activityService.insertBatch(
                List(batchSize) { ActivityEvent.Updated(user, System.currentTimeMillis(), "1", path) }
            )
        }

        runBlocking {
            val results = activityService.findEventsForFileId(NormalizedPaginationRequest(10,0), "1")
            assertEquals(2000, results.itemsInTotal)
        }

        runBlocking {
            val results = activityService.findEventsForUser(NormalizedPaginationRequest(10,0), user)
            assertEquals(2000, results.itemsInTotal)
        }

        runBlocking {
            val results = activityService.findEventsForPath(NormalizedPaginationRequest(10,0), user, "token", user)
            assertEquals(2000, results.itemsInTotal)
        }


        runBlocking {
            activityService.deleteOldActivity(numberOfDaysInPast)
        }


        runBlocking {
            val results = activityService.findEventsForFileId(NormalizedPaginationRequest(10,0), "1")
            assertEquals(1000, results.itemsInTotal)
        }

        runBlocking {
            val results = activityService.findEventsForUser(NormalizedPaginationRequest(10,0), user)
            assertEquals(1000, results.itemsInTotal)
        }

        runBlocking {
            val results = activityService.findEventsForPath(NormalizedPaginationRequest(10,0), user, "token", user)
            assertEquals(1000, results.itemsInTotal)
        }

    }
}

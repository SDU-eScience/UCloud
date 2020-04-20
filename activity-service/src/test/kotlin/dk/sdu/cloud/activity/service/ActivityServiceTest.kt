package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.api.ActivityEventType
import dk.sdu.cloud.activity.api.ActivityFilter
import dk.sdu.cloud.activity.api.ActivityForFrontend
import dk.sdu.cloud.activity.services.ActivityEventElasticDao
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.services.FileLookupService
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.NormalizedScrollRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.ScrollResult
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityServiceTest {
    val user = "user"
    val path = "/home/$user/file.txt"

    @Test
    fun `find by path test`() {
        val mockedFileLookup = mockk<FileLookupService>()
        val mockedDao = mockk<ActivityEventElasticDao>()
        val activityService = ActivityService(mockedDao, mockedFileLookup, ClientMock.authenticatedClient)

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
                SensitivityLevel.PRIVATE
            )
        }

        coEvery { mockedDao.findByFilePath(any(),any()) } answers {
            val items = listOf<ActivityForFrontend>(
                ActivityForFrontend(
                    ActivityEventType.deleted,
                    0L,
                    ActivityEvent.Deleted(
                        user,
                        0L,
                        path
                    )
                )
            )
            Page(2000, 10, 0, items)
        }

        runBlocking {
            val results = activityService.findEventsForPath(NormalizedPaginationRequest(10, 0), user, "token", user)
            assertEquals(2000, results.itemsInTotal)
        }
    }

    @Test
    fun `browse test`() {
        val mockedFileLookup = mockk<FileLookupService>()
        val mockedDao = mockk<ActivityEventElasticDao>()
        val activityService = ActivityService(mockedDao, mockedFileLookup, ClientMock.authenticatedClient)

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
                SensitivityLevel.PRIVATE
            )
        }

        coEvery { mockedDao.findUserEvents(any(),any()) } answers {
            listOf(
                ActivityEvent.Deleted(
                        user,
                        0L,
                        path
                )
            )
        }

        runBlocking {
            val results = activityService.browseActivity(NormalizedScrollRequest(0,250), user)
            assertTrue(results.endOfScroll)
            assertEquals(ActivityEventType.deleted, results.items.first().type)
        }
    }
}


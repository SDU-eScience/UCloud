package dk.sdu.cloud.activity.service

import dk.sdu.cloud.activity.services.ActivityEventElasticDao
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.activity.services.FileLookupService
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.NormalizedPaginationRequest
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
    lateinit var service: ActivityService



    @BeforeTest
    fun setupTest() {
        micro = initializeMicro()
        val dao = mockk<ActivityEventElasticDao>()
        service = ActivityService(dao, mockk(relaxed = true))
    }

    @Test
    fun `insert and find test`() {
        val mockedFileLookup = mockk<FileLookupService>()
        val mockedDao = mockk<ActivityEventElasticDao>()
        val activityService = ActivityService(mockedDao, mockedFileLookup)

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
            val results = activityService.findEventsForUser(NormalizedPaginationRequest(10, 0), user)
            assertEquals(2000, results.itemsInTotal)
        }

        runBlocking {
            val results = activityService.findEventsForPath(NormalizedPaginationRequest(10, 0), user, "token", user)
            assertEquals(2000, results.itemsInTotal)
        }
    }
}


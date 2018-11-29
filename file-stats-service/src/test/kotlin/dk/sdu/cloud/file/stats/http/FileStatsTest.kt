package dk.sdu.cloud.file.stats.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.stats.api.RecentFilesResponse
import dk.sdu.cloud.file.stats.api.SearchResult
import dk.sdu.cloud.file.stats.api.UsageResponse
import dk.sdu.cloud.file.stats.services.RecentFilesService
import dk.sdu.cloud.file.stats.services.UsageService
import dk.sdu.cloud.file.stats.storageFile
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class FileStatsTest {

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val usageService = mockk<UsageService>()
        val recentFilesService = mockk<RecentFilesService>()
        coEvery { usageService.calculateUsage(any(), any(), any())} returns 200
        coEvery { recentFilesService.queryRecentFiles(any(), any())} answers {
            val results = listOf(storageFile, storageFile.copy(id = "id2"))
            results.map {
                SearchResult(
                    it.path,
                    it.fileType,
                    it.annotations,
                    it.fileTimestamps.created,
                    it.id,
                    it.isLink,
                    it.fileTimestamps.modified,
                    it.owner,
                    it.sensitivityLevel
                )
            }
        }

        listOf(FileStatsController(recentFilesService, usageService))
    }

    @Test
    fun `test usage - with path`() {
        withKtorTest(
            setup,

            test = {
                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/files/stats/usage?path=/path/to/folder",
                    user = TestUsers.user
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<UsageResponse>(request.response.content!!)
                assertEquals(200, response.bytes)
                assertEquals("/path/to/folder", response.path)
            }
        )
    }

    @Test
    fun `test usage - without path`() {
        withKtorTest(
            setup,

            test = {
                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/files/stats/usage",
                    user = TestUsers.user
                )
                request.assertSuccess()
                val response = defaultMapper.readValue<UsageResponse>(request.response.content!!)
                assertEquals(200, response.bytes)
                assertEquals("/home/user/", response.path)
            }
        )
    }

    @Test
    fun `test recent`() {
        withKtorTest(
            setup,

            test = {
                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/files/stats/recent",
                    user = TestUsers.user
                )
                request.assertSuccess()

                val response =defaultMapper.readValue<RecentFilesResponse>(request.response.content!!)
                assertEquals(2, response.recentFiles.size)
                assertEquals("id", response.recentFiles.first().fileId)
                assertEquals("id2", response.recentFiles.last().fileId)
            }
        )
    }
}

package dk.sdu.cloud.file.stats.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.stats.api.RecentFilesResponse
import dk.sdu.cloud.file.stats.api.UsageResponse
import dk.sdu.cloud.file.stats.services.RecentFilesService
import dk.sdu.cloud.file.stats.services.UsageService
import dk.sdu.cloud.file.stats.storageFile
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class FileStatsTest {

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val micro = initializeMicro()
        val usageService = mockk<UsageService>()
        val recentFilesService = mockk<RecentFilesService>()
        coEvery { usageService.calculateUsage(any(), any(), any()) } returns 200
        coEvery { recentFilesService.queryRecentFiles(any(), any()) } answers {
            listOf(storageFile, storageFile.copy(fileId = "id2"))
        }

        listOf(FileStatsController(recentFilesService, usageService, ClientMock.authenticatedClient))
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
                ClientMock.mockCallSuccess(
                    FileDescriptions.findHomeFolder,
                    FindHomeFolderResponse("/home/user/")
                )

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

                val response = defaultMapper.readValue<RecentFilesResponse>(request.response.content!!)
                assertEquals(2, response.recentFiles.size)
                assertEquals("id", response.recentFiles.first().fileId)
                assertEquals("id2", response.recentFiles.last().fileId)
            }
        )
    }
}

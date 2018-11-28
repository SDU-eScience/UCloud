package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.service.RPCException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class UsageServiceTest{

    @Test
    fun `test calculate usage`() {
        mockkObject(QueryDescriptions) {
            coEvery { QueryDescriptions.statistics.call(any(), any()) } answers {
                val statisticsResponse = mockk<StatisticsResponse>()
                every { statisticsResponse.size?.sum } returns 200.0
                RESTResponse.Ok(
                    mockk(relaxed = true),
                    statisticsResponse
                )

            }
            val usageService = UsageService(mockk<AuthenticatedCloud>(relaxed = true))
            runBlocking {
                val used = usageService.calculateUsage("/home/", "user1")
                assertEquals(200, used)
            }
        }
    }

    @Test (expected = RPCException::class)
    fun `test calculate usage - Exception`() {
        mockkObject(QueryDescriptions) {
            coEvery { QueryDescriptions.statistics.call(any(), any()) } answers {
                val statisticsResponse = mockk<StatisticsResponse>()
                every { statisticsResponse.size?.sum } returns null
                RESTResponse.Ok(
                    mockk(relaxed = true),
                    statisticsResponse
                )

            }
            val usageService = UsageService(mockk<AuthenticatedCloud>(relaxed = true))
            runBlocking {
                usageService.calculateUsage("/home/", "user1")
            }
        }
    }
}

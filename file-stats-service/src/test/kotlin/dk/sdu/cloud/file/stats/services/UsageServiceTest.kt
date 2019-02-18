package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class UsageServiceTest {
    @Test
    fun `test calculate usage`() {
        ClientMock.mockCall(QueryDescriptions.statistics) {
            val statisticsResponse = mockk<StatisticsResponse>()
            every { statisticsResponse.size?.sum } returns 200.0
            TestCallResult.Ok(statisticsResponse)
        }

        val usageService = UsageService(ClientMock.authenticatedClient)
        runBlocking {
            val used = usageService.calculateUsage("/home/", "user1")
            assertEquals(200, used)
        }
    }

    @Test(expected = RPCException::class)
    fun `test calculate usage - Exception`() {
        ClientMock.mockCall(QueryDescriptions.statistics) {
            val statisticsResponse = mockk<StatisticsResponse>()
            every { statisticsResponse.size?.sum } returns null
            TestCallResult.Ok(statisticsResponse)
        }

        val usageService = UsageService(ClientMock.authenticatedClient)
        runBlocking {
            usageService.calculateUsage("/home/", "user1")
        }
    }
}

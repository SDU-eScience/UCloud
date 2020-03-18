package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.SizeResponse
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class UsageServiceTest {
    @Test
    fun `test calculate usage`() {
        ClientMock.mockCall(QueryDescriptions.size) {
            TestCallResult.Ok(SizeResponse(200L))
        }

        val usageService = UsageService(ClientMock.authenticatedClient)
        runBlocking {
            val used = usageService.calculateUsage("/home/user1/", "user1")
            assertEquals(200, used)
        }
    }

    @Test(expected = RPCException::class)
    fun `test calculate usage - Exception`() {
        ClientMock.mockCall(QueryDescriptions.size) {
            TestCallResult.Error(null, HttpStatusCode.InternalServerError)
        }

        val usageService = UsageService(ClientMock.authenticatedClient)
        runBlocking {
            usageService.calculateUsage("/home/user1/", "user1")
        }
    }
}

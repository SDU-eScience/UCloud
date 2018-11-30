package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.accounting.storage.Configuration
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.indexing.api.NumericStatistics
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

class StorageAccountingServiceTest {

    private val config = Configuration("0.1")

    @Test
    fun `test calculation`() {
        mockkObject(QueryDescriptions) {
            coEvery { QueryDescriptions.statistics.call(any(), any()) } answers {
                RESTResponse.Ok(
                    mockk(relaxed = true),
                    StatisticsResponse(
                        22,
                        NumericStatistics(null, null, null, 150.4, emptyList()),
                        NumericStatistics(null, null, null, null, emptyList())
                    )
                )
            }
            val cloud = mockk<AuthenticatedCloud>(relaxed = true)
            val storageAccountService = StorageAccountingService(cloud, config)
            runBlocking {
                val usedStorage = storageAccountService.calculateUsage("/home", "user")
                assertEquals("0.1", usedStorage.first().unitPrice.amount)
                assertEquals(150, usedStorage.first().units)
                val totalPrice =
                    usedStorage.first().unitPrice.amountAsDecimal.multiply(usedStorage.first().units.toBigDecimal())
                assertEquals(15,totalPrice.toInt())
            }
        }
    }

    @Test (expected = RPCException::class)
    fun `test calculation - NaN`() {
        mockkObject(QueryDescriptions) {
            coEvery { QueryDescriptions.statistics.call(any(), any()) } answers {
                val statisticResponse = mockk<StatisticsResponse>()
                every { statisticResponse.size?.sum } returns null
                RESTResponse.Ok(
                    mockk(relaxed = true),
                    statisticResponse
                )
            }
            val cloud = mockk<AuthenticatedCloud>(relaxed = true)
            val storageAccountService = StorageAccountingService(cloud, config)
            runBlocking {
                storageAccountService.calculateUsage("/home", "user")
            }
        }
    }

}

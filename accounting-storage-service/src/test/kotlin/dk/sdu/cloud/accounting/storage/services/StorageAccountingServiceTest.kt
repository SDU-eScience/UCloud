package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.storage.Configuration
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.indexing.api.NumericStatistics
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.test.withDatabase
import io.mockk.Invocation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import kotlin.test.assertEquals

class StorageAccountingServiceTest {

    private val config = Configuration("0.1")

    @Test
    fun `test calculation`() {
        withDatabase { db ->
            val micro = initializeMicro()
            val cloud = micro.authenticatedCloud

            CloudMock.mockCallSuccess(
                QueryDescriptions,
                { QueryDescriptions.statistics },
                StatisticsResponse(
                    22,
                    NumericStatistics(null, null, null, 150.4, emptyList()),
                    NumericStatistics(null, null, null, null, emptyList())
                )
            )

            val storageAccountService =
                StorageAccountingService(
                    cloud,
                    db,
                    StorageAccountingHibernateDao(),
                    config
                )
            runBlocking {
                val usedStorage = storageAccountService.calculateUsage("/home", "user")
                assertEquals("0.1", usedStorage.first().unitPrice.amount)
                assertEquals(150, usedStorage.first().units)
                val totalPrice =
                    usedStorage.first().unitPrice.amountAsDecimal.multiply(usedStorage.first().units.toBigDecimal())
                assertEquals(15, totalPrice.toInt())
            }
        }
    }

    @Test (expected = RPCException::class)
    fun `test calculation - NaN`() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val cloud = micro.authenticatedCloud

        val statisticResponse = mockk<StatisticsResponse>()
        every { statisticResponse.size?.sum } returns null

        CloudMock.mockCallSuccess(
            QueryDescriptions,
            { QueryDescriptions.statistics },
            statisticResponse
        )

        val storageAccountService =
            StorageAccountingService(
                cloud,
                micro.hibernateDatabase,
                StorageAccountingHibernateDao(),
                config
            )
        runBlocking {
            storageAccountService.calculateUsage("/home", "user")
        }
    }

    @Test
    fun `test generate data points`() = runBlocking {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val cloud = micro.authenticatedCloud
        CloudMock.mockCallSuccess(
            UserDescriptions,
            { UserDescriptions.openUserIterator },
            FindByStringId("1")
        )

        CloudMock.mockCallSuccess(
            UserDescriptions,
            { UserDescriptions.fetchNextIterator },
            emptyList()
        )

        CloudMock.mockCallSuccess(
            QueryDescriptions,
            { QueryDescriptions.statistics },
            StatisticsResponse(
                22,
                NumericStatistics(null, null, null, 150.4, emptyList()),
                NumericStatistics(null, null, null, null, emptyList())
            )
        )

        CloudMock.mockCallSuccess(
            UserDescriptions,
            { UserDescriptions.closeIterator },
            Unit
        )

        val storageAccountingService =
            StorageAccountingService(
                cloud,
                micro.hibernateDatabase,
                StorageAccountingHibernateDao(),
                config
            )
        storageAccountingService.collectCurrentStorageUsage()
    }
}

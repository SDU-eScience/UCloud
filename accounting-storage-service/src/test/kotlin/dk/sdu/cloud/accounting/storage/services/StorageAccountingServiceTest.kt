package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.ContextQueryImpl
import dk.sdu.cloud.accounting.storage.Configuration
import dk.sdu.cloud.accounting.storage.api.StorageUsedEvent
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.indexing.api.NumericStatistics
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageAccountingServiceTest {

    private val config = Configuration("0.1")

    fun setupService(): StorageAccountingService<HibernateSession> {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val cloud = ClientMock.authenticatedClient

        return StorageAccountingService(
            cloud,
            micro.hibernateDatabase,
            StorageAccountingHibernateDao(),
            config
        )
    }

    @Test
    fun `test calculation`() {
        val storageAccountService = setupService()

        ClientMock.mockCallSuccess(
            QueryDescriptions.statistics,
            StatisticsResponse(
                22,
                NumericStatistics(null, null, null, 150.4, emptyList()),
                NumericStatistics(null, null, null, null, emptyList())
            )
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

    @Test(expected = RPCException::class)
    fun `test calculation - NaN`() {
        val storageAccountService = setupService()
        val statisticResponse = mockk<StatisticsResponse>()
        every { statisticResponse.size?.sum } returns null

        ClientMock.mockCallSuccess(
            QueryDescriptions.statistics,
            statisticResponse
        )

        runBlocking {
            storageAccountService.calculateUsage("/home", "user")
        }
    }

    @Test
    fun `test generate data points`() = runBlocking {
        val storageAccountingService = setupService()

        ClientMock.mockCallSuccess(
            UserDescriptions.openUserIterator,
            FindByStringId("1")
        )

        var callCount = 0
        ClientMock.mockCall(
            UserDescriptions.fetchNextIterator,
            {
                callCount++
                when (callCount) {
                    1 -> TestCallResult.Ok(listOf(ServicePrincipal("_user", Role.SERVICE)), HttpStatusCode.OK)
                    2 -> TestCallResult.Ok(emptyList())
                    else -> TestCallResult.Error(CommonErrorMessage("ERROR"), HttpStatusCode.BadRequest)
                }
            }
        )

        ClientMock.mockCall(
            FileDescriptions.findHomeFolder,
            {
                TestCallResult.Ok(FindHomeFolderResponse("/home/${it.username}"))
            }
        )

        ClientMock.mockCallSuccess(
            QueryDescriptions.statistics,
            StatisticsResponse(
                22,
                NumericStatistics(null, null, null, 150.4, emptyList()),
                NumericStatistics(null, null, null, null, emptyList())
            )
        )

        ClientMock.mockCallSuccess(
            UserDescriptions.closeIterator,
            Unit
        )

        storageAccountingService.collectCurrentStorageUsage()
    }

    private val context = ContextQueryImpl(
        12345,
        Date().time
    )

    private val event = StorageUsedEvent(
        123456,
        1234,
        0,
        TestUsers.user.username
    )

    @Test(expected = RPCException::class)
    fun `List all Test`() {
        val storageAccountingService = setupService()
        storageAccountingService.listEvents(context, TestUsers.user.username)
    }

    @Test
    fun `List all Page Test`() {
        val storageAccountingService = setupService()
        assertTrue(
            storageAccountingService.listEventsPage(
                NormalizedPaginationRequest(10, 0),
                context,
                TestUsers.user.username
            ).items.isEmpty()
        )
    }
}

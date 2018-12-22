package dk.sdu.cloud.accounting.storage.http

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.readValues
import dk.sdu.cloud.accounting.api.BillableItem
import dk.sdu.cloud.accounting.api.BuildReportRequest
import dk.sdu.cloud.accounting.api.ChartResponse
import dk.sdu.cloud.accounting.api.Currencies
import dk.sdu.cloud.accounting.api.CurrentUsageResponse
import dk.sdu.cloud.accounting.api.SerializedMoney
import dk.sdu.cloud.accounting.storage.Configuration
import dk.sdu.cloud.accounting.storage.api.StorageUsedEvent
import dk.sdu.cloud.accounting.storage.services.StorageAccountingHibernateDao
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.accounting.storage.services.StorageForUserEntity
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.indexing.api.NumericStatistics
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withDatabase
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.h2.engine.Session
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue


private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
    micro.install(HibernateFeature)
    val storageAccountingDao = StorageAccountingHibernateDao()
    val storageAccountingService = StorageAccountingService(
        micro.authenticatedCloud,
        micro.hibernateDatabase,
        storageAccountingDao,
        Configuration("0.1")
    )

    withDatabase { db ->
        db.withTransaction { session ->
            for (i in 0..10) {
                session.save(StorageForUserEntity(
                    TestUsers.user.username,
                    Date(),
                    12345
                ))
            }
        }
    }

    CloudMock.mockCallSuccess(
        QueryDescriptions,
        { QueryDescriptions.statistics },
        StatisticsResponse(
            22,
            NumericStatistics(null, null, null, 150.4, emptyList()),
            NumericStatistics(null, null, null, null, emptyList())
        )
    )

    configureComputeTimeServer(storageAccountingService)
}

private fun KtorApplicationTestSetupContext.configureComputeTimeServer(
    storageAccountingService: StorageAccountingService<HibernateSession>
): List<Controller> {
    return listOf(
        StorageUsedController(storageAccountingService),
        StorageAccountingController(storageAccountingService)
    )
}

class StorageUsedTest{

    @Test
    fun `list events`() {
        withKtorTest(
            setup,
            test= {

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/storage/bytesUsed/events",
                    user = TestUsers.user,
                    params = mapOf( "since" to "12345")
                )

                request.assertSuccess()
                val response = defaultMapper.readValue<Page<StorageUsedEvent>>(request.response.content!!)
                println(response)
                assertEquals(11, response.itemsInTotal)
                assertEquals(2, response.pagesInTotal)
                assertEquals(0, response.pageNumber)
                assertEquals(12345, response.items.first().bytesUsed)
            }
        )
    }

    @Test
    fun `chart test`() {
        withKtorTest(
            setup,
            test= {
                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/storage/bytesUsed/chart",
                    user = TestUsers.user,
                    params = mapOf( "since" to "12345")
                )
                request.assertSuccess()

                //TODO Works but not pretty
                assertTrue(request.response.content?.contains("\"dataTypes\":[\"datetime\",\"bytes\"],\"dataTitle\":\"Storage Used\"")!!)

            }
        )
    }

    @Test
    fun `Test currentUsage`() {
        withKtorTest(
            setup,
            test = {
                with(engine) {
                    run {

                        val request = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/accounting/storage/bytesUsed/usage",
                            user = TestUsers.user
                        )
                        request.assertSuccess()

                        val items = defaultMapper.readValue<CurrentUsageResponse>(request.response.content!!)
                        assertEquals(150, items.usage)
                    }
                }
            }
        )
    }
}

package dk.sdu.cloud.accounting.storage.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.accounting.api.BillableItem
import dk.sdu.cloud.accounting.api.BuildReportRequest
import dk.sdu.cloud.accounting.api.BuildReportResponse
import dk.sdu.cloud.accounting.api.Currencies
import dk.sdu.cloud.accounting.api.SerializedMoney
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.h2.engine.Session
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class StorageAccountingTest {
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val storageAccountingService = mockk<StorageAccountingService<Session>>(relaxed = true)
        coEvery { storageAccountingService.calculateUsage(any(), any(), any()) } answers {
            val invoice = listOf(BillableItem("Used Storage", 150, SerializedMoney(BigDecimal("0.1"), Currencies.DKK)))
            invoice
        }
        listOf(StorageAccountingController(storageAccountingService))
    }

    @Test
    fun `Build Report - storage - service call`() {
        withKtorTest(
            setup,
            test= {
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/accounting/storage/buildReport",
                    user = TestUsers.service.copy(username = "_accounting"),
                    request = BuildReportRequest(
                        "user1",
                        1,
                        1234567
                    )
                )

                request.assertStatus(HttpStatusCode.OK)
                val response = defaultMapper.readValue<BuildReportResponse>(request.response.content!!)
                assertEquals("Used Storage", response.items.first().name)
                assertEquals(150, response.items.first().units)
            }
        )
    }

    @Test
    fun `Build Report - storage - user call`() {
        withKtorTest(
            setup,
            test= {
                sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/accounting/storage/buildReport",
                    user = TestUsers.user
                ).assertStatus(HttpStatusCode.Unauthorized)
            }
        )
    }
}


package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Test

class AbstractAccountingDescriptionsTest {
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        configureTestServer()
    }

    private fun KtorApplicationTestSetupContext.configureTestServer(
    ): List<Controller> {
        return listOf(TestController())
    }

    @Test
    fun `Build Report service call` () {
        withKtorTest(
            setup = setup,
            test = {
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/accounting/namespace/buildReport",
                    user = TestUsers.service.copy(username = "_accounting"),
                    request = BuildReportRequest(
                        "user1",
                        1,
                        1234567
                    )
                )

                request.assertSuccess()
            }
        )
    }

    @Test
    fun `Build Report user call`() {
        withKtorTest(
            setup,
            test = {
                sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/accounting/namespace/buildReport",
                    user = TestUsers.user
                ).assertStatus(HttpStatusCode.Unauthorized)
            }
        )
    }

    @Test
    fun `List Resources test` () {
        withKtorTest(
            setup = setup,
            test = {
                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/namespace/list",
                    user = TestUsers.admin
                )

                request.assertSuccess()
            }
        )
    }

}

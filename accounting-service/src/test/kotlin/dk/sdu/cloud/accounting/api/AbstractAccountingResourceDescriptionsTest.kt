package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import org.junit.Test

class AbstractAccountingResourceDescriptionsTest {
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        configureTestServer()
    }

    private fun KtorApplicationTestSetupContext.configureTestServer(
    ): List<Controller> {
        return listOf(TestResourceController())
    }

    @Test
    fun `list events`() {
        withKtorTest(
            setup,
            test = {

                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/namespace/bytesUsed/events",
                    user = TestUsers.user,
                    params = mapOf("since" to "12345")
                )

                request.assertSuccess()
            }
        )
    }

    @Test
    fun `chart test`() {
        withKtorTest(
            setup,
            test = {
                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/accounting/namespace/bytesUsed/chart",
                    user = TestUsers.user,
                    params = mapOf("since" to "12345")
                )
                request.assertSuccess()
            }
        )
    }

    @Test
    fun `Test Usage - no params`() {
        withKtorTest(
            setup,
            test = {

                run {
                    val request = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/accounting/namespace/bytesUsed/usage",
                        user = TestUsers.user
                    )
                    request.assertSuccess()
                }
            }
        )
    }
}

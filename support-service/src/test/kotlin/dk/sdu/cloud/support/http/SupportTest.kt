package dk.sdu.cloud.support.http

import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertFailure
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.withKtorTest
import dk.sdu.cloud.support.api.CreateTicketRequest
import dk.sdu.cloud.support.services.TicketService
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import org.junit.Test

class SupportTest{

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val service = mockk<TicketService>()
        coEvery {service.createTicket(any()) } just Runs
        listOf(SupportController(service))
    }

    @Test
    fun `create test`() {
        withKtorTest(
            setup,
            test = {
                sendJson(
                    method = HttpMethod.Post,
                    path = "/api/support/ticket",
                    user = TestUsers.user,
                    request =  CreateTicketRequest("This is message")
                ).assertSuccess()
            }
        )
    }

    @Test
    fun `create test - to long a message`() {
        withKtorTest(
            setup,
            test = {
                sendJson(
                    method = HttpMethod.Post,
                    path = "/api/support/ticket",
                    user = TestUsers.user,
                    request =  CreateTicketRequest("This is a message".repeat(53000))
                ).assertStatus(HttpStatusCode.PayloadTooLarge)
            }
        )
    }

    @Test
    fun `create test - to many requests`() {
        withKtorTest(
            setup,
            test = {
                for (i in 1..10) {
                    sendJson(
                        method = HttpMethod.Post,
                        path = "/api/support/ticket",
                        user = TestUsers.user,
                        request = CreateTicketRequest("This is a message")
                    ).assertSuccess()
                }
                //Request 11 within an hour should fail.
                sendJson(
                    method = HttpMethod.Post,
                    path = "/api/support/ticket",
                    user = TestUsers.user,
                    request = CreateTicketRequest("This is a message")
                ).assertFailure()
            }
        )
    }
}

package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.support.api.CreateTicketRequest
import dk.sdu.cloud.support.api.SupportDescriptions

class SupportTesting(private val userAndClient: UserAndClient) {

    suspend fun runTest() {
        sendTicket()
    }

    private suspend fun sendTicket() {
        SupportDescriptions.createTicket.call(
            CreateTicketRequest("Hello from integration testing"),
            userAndClient.client
        )
    }
}
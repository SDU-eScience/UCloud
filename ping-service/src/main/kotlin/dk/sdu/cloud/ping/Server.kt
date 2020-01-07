package dk.sdu.cloud.ping

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.ping.services.ConsumerTest
import dk.sdu.cloud.ping.services.RegularCallTest
import dk.sdu.cloud.ping.services.WSSubscriptionTest
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val wsClient = AuthenticatedClient(micro.client, OutgoingWSCall, authenticator = {})
        val httpClient = AuthenticatedClient(micro.client, OutgoingHttpCall, authenticator = {})
        val streams = micro.eventStreamService

        ConsumerTest(streams).startTest()
        val wsCallTest = RegularCallTest("WS Call", wsClient).startTest()
        val httpCallTest = RegularCallTest("HTTP Call", httpClient).startTest()
        val subscriptionTest = WSSubscriptionTest(wsClient).startTest()

        startServices(wait = false)

        runBlocking {
            wsCallTest.join()
            httpCallTest.join()
            subscriptionTest.join()
        }
    }

    override fun stop() {
        super.stop()
    }
}

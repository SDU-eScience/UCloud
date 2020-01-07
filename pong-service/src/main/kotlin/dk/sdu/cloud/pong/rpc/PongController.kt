package dk.sdu.cloud.pong.rpc

import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.pong.api.Pongs
import dk.sdu.cloud.pong.api.SubscriptionResponse
import dk.sdu.cloud.pong.services.ResultTracker
import dk.sdu.cloud.pong.services.TestResult
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.random.Random

class PongController : Controller {
    private val subscriptionTracker = ResultTracker("Subscription Tracker")
    private val httpTracker = ResultTracker("HTTP Call")
    private val wsTracker = ResultTracker("WS Call")

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        subscriptionTracker.start()
        httpTracker.start()
        wsTracker.start()

        implement(Pongs.subscription) {
            log.info("We will now reply with ${request.request}")

            var counter = 0
            while (true) {
                val numberOfMessagesToSend = Random.nextInt(1, 10)

                coroutineScope {
                    (0 until numberOfMessagesToSend).map {
                        launch {
                            subscriptionTracker.trackResult(TestResult(System.currentTimeMillis(), true))
                            sendWSMessage(SubscriptionResponse("${request.request} $counter"))
                        }
                    }.joinAll()
                }

                counter++
                delay(30_000)
            }
        }

        implement(Pongs.regularCall) {
            val tracker = if (ctx is HttpCall) httpTracker else wsTracker
            tracker.trackResult(TestResult(System.currentTimeMillis(), true))
            ok(request)
        }
        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}

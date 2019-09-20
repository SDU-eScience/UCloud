package dk.sdu.cloud.rpc.test.b.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.rpc.test.b.api.PingResponse
import dk.sdu.cloud.rpc.test.b.api.TestB
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class TestController : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(TestB.ping) {
            // Results from this show that response times clearly explode when enough requests are on the line. With
            // high parallelism this reached a response time of 300ms.
            ok(PingResponse(request.ping))
        }

        implement(TestB.processing) {
            // This clearly shows that code blocking on this thread will cause no more requests to go through. This
            // is likely a problem we are facing in app-kubernetes at the moment. We must be more strict on ensuring
            // that work is _not_ being performed on this thread.

            repeat(10) {
                Thread.sleep(1000)
            }

            ok(Unit)
        }

        implement(TestB.suspendingProcessing) {
            // Even using launch inside this will make response times explode. With a parallelism of 128 this ended
            // up taking 30 times longer than in ideal case (100ms).

            val time = measureTimeMillis {
                coroutineScope {
                    repeat(100) {
                        launch {
                            delay(100)
                        }
                    }
                }
            }

            log.debug("Took: $time")

            ok(Unit)
        }

        implement(TestB.suspendingProcessing2) {
            // Scheduling tasks in the GlobalScope does not change anything. We are getting times back similar to
            // that of suspendingProcessing. This is fairly odd given that a test doing the exact thing is having
            // nearly _no_ overhead. The TestKt file shows an overhead a lot closer to 2-3ms.

            val time = measureTimeMillis {
                (0 until 100).map {
                    GlobalScope.launch {
                        delay(100)
                    }
                }.joinAll()
            }

            log.debug("Took: $time")
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

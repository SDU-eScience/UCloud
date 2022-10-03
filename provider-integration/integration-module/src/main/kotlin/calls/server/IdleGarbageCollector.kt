package dk.sdu.cloud.calls.server

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class IdleGarbageCollector {
    fun register(server: RpcServer) {
        val lastRequest = AtomicLong(Time.now())

        server.attachFilter(object : IngoingCallFilter.AfterParsing() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true

            override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>, request: Any) {
                lastRequest.compareAndSet(lastRequest.get(), Time.now())
            }
        })

        ProcessingScope.launch {
            var lastGcTime = 0L
            while (isActive) {
                val now = Time.now()
                val lastRequestTime = lastRequest.get()
                if (now - lastRequestTime > 1000L * 30 && now - lastGcTime > 1000L * 60 * 10) {
                    log.info("Triggering idle GC")
                    lastGcTime = now
                    System.gc()
                }
                delay(5000)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

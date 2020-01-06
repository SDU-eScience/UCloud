package dk.sdu.cloud.ping.services

import dk.sdu.cloud.service.Loggable
import io.mockk.internalSubstitute
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

data class TestResult(val timestamp: Long, val success: Boolean, val responseTime: Long? = null)

class ResultTracker(private val testName: String) {
    private val isRunning = AtomicBoolean(false)
    private val allResults = ArrayList<TestResult>()
    private val mutex = Mutex()

    suspend fun trackResult(result: TestResult) {
        mutex.withLock {
            allResults.add(result)
        }
    }

    fun start() {
        require(isRunning.compareAndSet(false, true))

        GlobalScope.launch {
            while (isActive && isRunning.get()) {
                mutex.withLock {
                    val averageResponseTime = allResults.mapNotNull { it.responseTime }.average()
                    val mostRecentFailures = allResults.filter { !it.success }.takeLast(5)
                    val numberOfRequests = allResults.size

                    log.info("[$testName] Average response time: $averageResponseTime")
                    log.info("[$testName] Most recent failures: $mostRecentFailures")
                    log.info("[$testName] Total number of requests: $numberOfRequests")
                }

                delay(60_000)
            }
        }
    }

    fun stop() {
        isRunning.set(false)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

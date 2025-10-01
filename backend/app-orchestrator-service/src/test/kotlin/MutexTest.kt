package dk.sdu.cloud.app.orchestrator.services

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MutexTest {
    @Test
    @Ignore
    fun `test deadlock detection`() {
        val l1 = DebuggingMutex()
        val l2 = DebuggingMutex()
        runBlocking {
            val j1 = GlobalScope.launch {
                l1.lock("1a")
                delay(1000)
                l2.lock("1b")
            }

            val j2 = GlobalScope.launch {
                l2.lock("2a")
                delay(1000)
                l1.lock("2b")
            }

            j1.join()
            j2.join()
        }
    }
}

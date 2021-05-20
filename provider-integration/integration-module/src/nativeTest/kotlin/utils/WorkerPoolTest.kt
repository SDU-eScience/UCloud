package dk.sdu.cloud.utils

import dk.sdu.cloud.freeze
import dk.sdu.cloud.utils.DynamicWorkerPool
import kotlinx.atomicfu.atomic
import platform.posix.usleep
import kotlin.system.getTimeMillis
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerPoolTest {
    @Test
    fun `test creating a single thread`() {
        val pool = DynamicWorkerPool("Test")
        val result = atomic<String?>(null).freeze()
        val value = "Hello, World!"

        pool.start()
        pool.execute({ result }) { result ->
            result.getAndSet(value)
        }

        usleep(10_000)
        pool.close()

        assertEquals(value, result.value)
    }

    @Test
    fun `test creating a small number of jobs`() {
        val pool = DynamicWorkerPool("Test", minimumPoolSize = 5)
        pool.start()
        val counter = atomic(0).freeze()
        val times = 5
        repeat(times) {
            pool.execute({ counter }, { counter.incrementAndGet() })
        }
        usleep(10_000)
        pool.close()
        assertEquals(times, counter.value)
    }

    @Test
    fun `test full queue`() {
        val pool = DynamicWorkerPool("Test", minimumPoolSize = 1, maximumPoolSize = 1)
        pool.start()
        val counter = atomic(0).freeze()
        val times = 1000
        repeat(times) {
            pool.execute({ counter }, {
                usleep(100)
                counter.incrementAndGet()
            })
        }
        usleep(10_000)
        val start = getTimeMillis()
        while (getTimeMillis() - start < 600) {
            if (times == counter.value) break
            usleep(100)
        }
        pool.close()
        assertEquals(times, counter.value)
    }

    @Test
    fun `test worker creation`() {
        val pool = DynamicWorkerPool("Test", minimumPoolSize = 1, maximumPoolSize = 5)
        pool.start()
        val counter = atomic(0).freeze()
        val times = 5
        val time = measureTimeMillis {
            repeat(times) {
                pool.execute({ counter }) {
                    usleep(1_000_000)
                    counter.incrementAndGet()
                }
            }


            val start = getTimeMillis()
            while (getTimeMillis() - start < 5_000) {
                if (counter.value == times) break
            }
        }

        pool.close()
        assertTrue("Expected workers to finish in less than 3 seconds but took ${time}ms") {
            time <= 3000
        }
        assertEquals(times, counter.value)
    }
}
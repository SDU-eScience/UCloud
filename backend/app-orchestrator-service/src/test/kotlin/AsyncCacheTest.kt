package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.micro.BackgroundScope
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.test.*

class AsyncCacheTest {
    private fun createScope(): BackgroundScope = BackgroundScope().also { it.init() }
    private fun <V> timeoutFunction(): (key: V) -> Nothing {
        return { error("Failure: $it") }
    }

    @Test
    fun `test simple usage`() = runBlocking {
        val counter = AtomicInteger(0)
        val cache = AsyncCache<Int, Int>(
            createScope(),
            timeoutException = timeoutFunction(),
            retrieve = {
                counter.incrementAndGet()
                it * 2
            }
        )

        repeat(100) {
            assertEquals(cache.retrieve(2), 4)
        }
        assertEquals(1, counter.get())

        repeat(100) {
            assertEquals(cache.retrieve(2), 4)
            assertEquals(cache.retrieve(4), 8)
        }
        assertEquals(2, counter.get())
    }

    @Test
    fun `test eager simple usage`() = runBlocking {
        val counter = AtomicInteger(0)
        val cache = AsyncCache<Int, Int>(
            createScope(),
            fetchEagerly = true,
            timeoutException = timeoutFunction(),
            retrieve = {
                counter.incrementAndGet()
                it * 2
            }
        )

        repeat(100) {
            assertEquals(cache.retrieve(2), 4)
        }
        assertEquals(1, counter.get())

        repeat(100) {
            assertEquals(cache.retrieve(2), 4)
            assertEquals(cache.retrieve(4), 8)
        }
        assertEquals(2, counter.get())
    }

    @Test
    fun `test resilience against race-conditions`() = runBlocking {
        val counter = AtomicInteger(0)
        val backgroundScope = createScope()
        val cache = AsyncCache<Int, Int>(
            backgroundScope,
            timeoutException = timeoutFunction(),
            retrieve = {
                counter.incrementAndGet()
                delay(100)
                it * 2
            }
        )

        (0 until max(Runtime.getRuntime().availableProcessors(), 2)).map {
            backgroundScope.async { assertEquals(cache.retrieve(100), 200) }
        }.awaitAll()
        assertEquals(1, counter.get())
    }

    @Test
    fun `test eager fetching without failures`() = runBlocking {
        val counter = AtomicInteger(0)
        val backgroundScope = createScope()
        val cache = AsyncCache<Int, Int>(
            backgroundScope,
            fetchEagerly = true,
            timeoutException = timeoutFunction(),
            retrieve = {
                counter.incrementAndGet()
                delay(100)
                it * 2
            }
        )

        (0 until max(Runtime.getRuntime().availableProcessors(), 2)).map {
            backgroundScope.async { assertEquals(cache.retrieve(100), 200) }
        }.awaitAll()
        assertEquals(1, counter.get())
    }

    @Test
    fun `test eager fetching with failures`() = runBlocking {
        val counter = AtomicInteger(0)
        val backgroundScope = createScope()
        val cache = AsyncCache<Int, Int>(
            backgroundScope,
            timeToLiveMilliseconds = 100,
            fetchEagerly = true,
            timeoutException = timeoutFunction(),
            retrieve = {
                counter.incrementAndGet()
                if (counter.get() % 2 == 0) error("Failure!")
                delay(100)
                it * 2
            }
        )

        (0 until max(Runtime.getRuntime().availableProcessors(), 2)).map {
            backgroundScope.async { assertEquals(cache.retrieve(200), 400) }
        }.awaitAll()
        val initialCounter = counter.get()
        assertTrue(initialCounter in 1..10)
        delay(200)

        (0 until max(Runtime.getRuntime().availableProcessors(), 2)).map {
            backgroundScope.async { assertEquals(cache.retrieve(200), 400) }
        }.awaitAll()
        assertTrue(counter.get() in 2..10)
        assertTrue(counter.get() > initialCounter)
    }

    @Test
    fun `test eager with changing data`() = runBlocking {
        val counter = AtomicInteger(0)
        val backgroundScope = createScope()
        val cache = AsyncCache<Int, Int>(
            backgroundScope,
            timeToLiveMilliseconds = 100,
            fetchEagerly = true,
            timeoutException = timeoutFunction(),
            retrieve = {
                val newResult = counter.incrementAndGet()
                if (counter.get() % 2 == 0) error("Failure!")
                newResult
            }
        )

        (0 until max(Runtime.getRuntime().availableProcessors(), 2)).map {
            backgroundScope.async {
                assertTrue(cache.retrieve(200) in 1..10)
            }
        }.awaitAll()
        delay(6000)

        (0 until max(Runtime.getRuntime().availableProcessors(), 2)).map {
            backgroundScope.async {
                assertTrue(cache.retrieve(200) in 50..100)
            }
        }.awaitAll()

        Unit
    }

    @Test
    fun `test eager with timeouts on some requests`() = runBlocking {
        val counter = AtomicInteger(0)
        val backgroundScope = createScope()
        val cache = AsyncCache<Int, Int>(
            backgroundScope,
            timeToLiveMilliseconds = 100,
            timeoutMilliseconds = 1000,
            fetchEagerly = true,
            timeoutException = timeoutFunction(),
            retrieve = {
                if (counter.incrementAndGet() >= 2) {
                    delay(2000)
                    0
                } else {
                    42
                }
            }
        )

        (0 until max(Runtime.getRuntime().availableProcessors(), 2)).map {
            backgroundScope.async { assertTrue(cache.retrieve(200) == 42) }
        }.awaitAll()

        delay(3000)

        (0 until max(Runtime.getRuntime().availableProcessors(), 2)).map {
            backgroundScope.async { assertTrue(cache.retrieve(200) == 42) }
        }.awaitAll()

        Unit
    }

    @Test
    fun `test eager with constant failure`() = runBlocking {
        val message = "This should always fail"
        val backgroundScope = createScope()
        val cache = AsyncCache<Int, Int>(
            backgroundScope,
            timeToLiveMilliseconds = 100,
            timeoutMilliseconds = 1000,
            fetchEagerly = true,
            timeoutException = timeoutFunction(),
            retrieve = { error(message) }
        )

        try {
            cache.retrieve(42)
            assertTrue(false)
        } catch (ex: Throwable) {
            assertEquals(message, ex.message)
        }
    }

    @Test
    fun `test eager with failure recovery`() = runBlocking {
        val count = AtomicInteger(0)
        val backgroundScope = createScope()
        val message = "This should fail in the beginning"
        val cache = AsyncCache<Int, Int>(
            backgroundScope,
            timeToLiveMilliseconds = 100,
            timeoutMilliseconds = 1000,
            fetchEagerly = true,
            timeoutException = timeoutFunction(),
            retrieve = {
                if (count.incrementAndGet() <= 1) {
                    error(message)
                }

                42
            }
        )

        try {
            cache.retrieve(42)
            assertTrue(false)
        } catch (ex: Throwable) {
            // It should fail
            assertEquals(message, ex.message)
        }

        delay(1000)

        assertEquals(42, cache.retrieve(42))
    }
}

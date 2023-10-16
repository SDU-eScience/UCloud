package dk.sdu.cloud.app.orchestrator.services

import kotlinx.coroutines.*
import kotlin.test.*

class ShardedSortedIntegerSetTest {
    @Test
    fun `empty test`() = runBlocking {
        val output = IntList()
        val set = ShardedSortedIntegerSet()
        set.findValues(output, 0)
        assertEquals(0, output.size)
    }

    @Test
    fun `single test`() = runBlocking {
        val output = IntList()
        val set = ShardedSortedIntegerSet()
        set.add(42)
        set.findValues(output, 0)
        assertEquals(listOf(42), output.toList())
    }

    @Test
    fun `filter test`() = runBlocking {
        val output = IntList()
        val set = ShardedSortedIntegerSet()
        val range = 0..<1000
        range.forEach { set.add(it) }

        set.findValues(output, 0)
        assertEquals(range.toList(), output.toList())

        output.clear()

        set.findValues(output, 900)
        assertEquals((900..<1000).toList(), output.toList())
    }

    @Test
    fun `random order`() = runBlocking {
        val output = IntList()
        val set = ShardedSortedIntegerSet()
        val range = 0..<1000

        val randomList = range.toMutableList()
        while (randomList.isNotEmpty()) {
            val elem = randomList.removeAt(randomList.indices.random())
            set.add(elem)
        }

        set.findValues(output, 0)
        assertEquals(range.toList(), output.toList())
    }

    @Test
    fun `test zero`() = runBlocking {
        val output = IntList()
        val set = ShardedSortedIntegerSet()

        set.add(0)
        set.findValues(output, 0)
        assertEquals(listOf(0), output.toList())
    }

    @Test
    fun `concurrent test (shards = 1)`() = runBlocking {
        concurrentTest(1)
    }

    @Test
    fun `concurrent test (shards = 16)`() = runBlocking {
        concurrentTest(16)
    }

    @Test
    fun `concurrent test (shards = 64)`() = runBlocking {
        concurrentTest(64)
    }

    @Test
    fun `concurrent test (shards = 256)`() = runBlocking {
        concurrentTest(256)
    }

    @Test
    fun `concurrent test (shards = 512)`() = runBlocking {
        concurrentTest(512)
    }

    @Test
    fun `concurrent test (shards = 1024)`() = runBlocking {
        concurrentTest(1024)
    }

    private suspend fun concurrentTest(count: Int) {
        val set = ShardedSortedIntegerSet(count)
        coroutineScope {
            (0..<100).map { ci ->
                launch(Dispatchers.IO) {
                    val base = 100_000 * ci
                    repeat(1_000) {
                        set.add(base + it)
                    }
                }
            }.joinAll()
        }
    }

    @Test
    fun `baseline addition to list`() {
        val list = IntList()
        repeat(1_000_000) {
            list.add(it)
        }
    }
}

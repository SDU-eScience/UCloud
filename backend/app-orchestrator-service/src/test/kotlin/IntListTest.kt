package dk.sdu.cloud.app.orchestrator.services

import kotlin.test.*

class IntListTest {
    @Test
    fun `empty list`() {
        val list = IntList()
        assertEquals(0, list.size)
        assertEquals(emptyList(), list.toList())
    }

    @Test
    fun `add test`() {
        addTest(10)
    }

    private fun addTest(count: Int) {
        val list = IntList()
        repeat(count) { list.add(it) }
        assertEquals(count, list.size)
        repeat(count) { assertEquals(it, list.indexOf(it)) }
        assertEquals(-1, list.indexOf(Int.MAX_VALUE))

        val boxedList = ArrayList<Int>()
        repeat(count) { boxedList.add(it) }
        assertEquals(boxedList, list.toList())
    }

    @Test
    fun `grow test`() {
        addTest(4096)
    }

    @Test
    fun `grow test large`() {
        addTest(1024 * 64)
    }

    @Test
    @Ignore
    fun `speed comparison large`() {
        val count = 1024 * 64
        val list = ArrayList<Int>()
        repeat(count) { list.add(it) }
        assertEquals(count, list.size)
        repeat(count) { assertEquals(it, list.indexOf(it)) }
        assertEquals(-1, list.indexOf(Int.MAX_VALUE))

        val boxedList = ArrayList<Int>()
        repeat(count) { boxedList.add(it) }
        assertEquals(boxedList, list.toList())
    }

    @Test
    fun `test indexOf out of bounds`() {
        val list = IntList()
        assertEquals(-1, list.indexOf(0))
        list.add(1)
        assertEquals(0, list.indexOf(1))
    }

    @Test
    fun `add sorted set`() {
        val list = IntList()
        list.addSortedSet(4)
        assertEquals(listOf(4), list.toList())
        list.addSortedSet(2)
        assertEquals(listOf(2, 4), list.toList())
        list.addSortedSet(1)
        assertEquals(listOf(1, 2, 4), list.toList())
        list.addSortedSet(1)
        assertEquals(listOf(1, 2, 4), list.toList())
        list.addSortedSet(3)
        assertEquals(listOf(1, 2, 3, 4), list.toList())
        list.addSortedSet(5)
        assertEquals(listOf(1, 2, 3, 4, 5), list.toList())
        list.addSortedSet(6)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), list.toList())

        assertEquals(6, list.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), list.toList())
    }

    @Test
    fun `add sorted set grow`() {
        val list = IntList()
        repeat(100) {
            list.addSortedSet(it)
        }
    }

    @Test
    fun `add sorted set grow reverse`() {
        val list = IntList()
        (0 until 100).reversed().forEach {
            list.addSortedSet(it)
        }
    }
}

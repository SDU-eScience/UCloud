package dk.sdu.cloud.debug

class CircularList<E : Any>(val capacity: Int) : Iterable<E> {
    private var head: Int = 0
    private var tail: Int = 0
    private val array = arrayOfNulls<Any?>(capacity)

    val size: Int
        get() {
            return if (tail >= head) tail - head
            else capacity - head + tail
        }

    override fun iterator(): Iterator<E> {
        return object : Iterator<E> {
            var ptr = 0
            val actualSize = size

            override fun hasNext(): Boolean = ptr < actualSize
            override fun next(): E = get(ptr++)
        }
    }

    fun reverseIterator(): Iterator<E> {
        return object : Iterator<E> {
            var ptr = size - 1

            override fun hasNext(): Boolean = ptr >= 0
            override fun next(): E = get(ptr--)
        }
    }

    fun add(elem: E) {
        array[tail++] = elem
        tail %= capacity
        if (head >= tail) head = tail + 1
    }

    fun addAll(collection: Collection<E>) {
        for (item in collection) add(item)
    }

    fun clear() {
        head = 0
        tail = 0
    }

    operator fun get(index: Int): E {
        @Suppress("UNCHECKED_CAST")
        return array[(head + index) % capacity] as? E? ?: throw NoSuchElementException()
    }

    fun getOrNull(index: Int): E? {
        if (index < 0) return null
        if (index >= size) return null
        return get(index)
    }
}

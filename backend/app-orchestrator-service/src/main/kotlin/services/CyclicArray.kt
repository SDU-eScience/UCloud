package dk.sdu.cloud.app.orchestrator.services

/**
 * A cyclic array containing the latest [capacity] elements.
 *
 * Once a new element is added, beyond the [capacity], then the oldest element is removed from the array. The oldest
 * element is stored at index 0, while the newest element is stored at index [capacity] - 1.
 *
 * @param capacity The maximum number of elements the array can hold.
 * @param T The type of elements stored in the array.
 */
class CyclicArray<T>(val capacity: Int) : Iterable<T> {
    private val data = arrayOfNulls<Any>(capacity)
    private var head = 0
    var size: Int = 0
        private set

    fun add(element: T) {
        if (size < capacity) {
            data[size] = element
            size++
        } else {
            data[head] = element
            head = (head + 1) % capacity
        }
    }

    operator fun get(index: Int): T {
        require(index in 0 until size) { "index out of bounds $index !in 0..<$size" }
        @Suppress("UNCHECKED_CAST")
        return data[(head + index) % capacity] as T
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            var offset = 0

            override fun hasNext(): Boolean = offset < size

            override fun next(): T {
                @Suppress("UNCHECKED_CAST")
                return data[(head + (offset++)) % capacity] as T
            }
        }
    }
}

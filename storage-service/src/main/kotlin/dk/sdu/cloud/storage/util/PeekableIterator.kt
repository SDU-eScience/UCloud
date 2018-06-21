package dk.sdu.cloud.storage.util

class PeekableIterator<T : Any>(private val delegate: Iterator<T>) : Iterator<T> {
    private var cached: T? = null

    override fun hasNext(): Boolean {
        if (cached != null) return true

        return if (!delegate.hasNext()) {
            false
        } else {
            cached = delegate.next()
            true
        }
    }

    override fun next(): T {
        val current = cached
        return if (current != null) {
            cached = null
            current
        } else {
            if (hasNext()) {
                cached!!
            } else {
                throw NoSuchElementException()
            }
        }
    }

    fun peek(): T {
        if (hasNext()) return cached!!
        throw NoSuchElementException()
    }
}

fun <T : Any> Iterable<T>.peekableIterator(): PeekableIterator<T> = PeekableIterator(iterator())
fun <T : Any> Sequence<T>.peekableIterator(): PeekableIterator<T> = PeekableIterator(iterator())

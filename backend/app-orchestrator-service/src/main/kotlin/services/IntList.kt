package dk.sdu.cloud.app.orchestrator.services

import jdk.incubator.vector.IntVector
import jdk.incubator.vector.LongVector
import jdk.incubator.vector.VectorOperators

// NOTE(Dan): A mutable list which contains primitive integers. It supports vectorized indexOf. Quick benchmarks
// show that this is roughly 10x faster than using a boxed ArrayList<Int>.
@Suppress("NOTHING_TO_INLINE")
class IntList : Iterable<Int> {
    var _data = IntArray(32)
    var size = 0
        private set

    inline operator fun get(index: Int): Int = _data[index]
    inline operator fun set(index: Int, value: Int) {
        _data[index] = value
    }

    fun clear() {
        size = 0
    }

    fun add(value: Int) {
        if (size == _data.size) grow()
        _data[size++] = value
    }

    inline fun addIfDistinct(value: Int) {
        if (indexOf(value) == -1) add(value)
    }

    fun addAtIndex(value: Int, index: Int) {
        if (size >= _data.size - 1) grow()
        System.arraycopy(_data, index, _data, index + 1, size - index)
        _data[index] = value
        size++
    }

    fun addSortedSet(value: Int) {
        val indexOrNegativeIfFound = searchForSlot(value)
        if (indexOrNegativeIfFound < 0) return
        addAtIndex(value, indexOrNegativeIfFound)
    }

    private fun searchForSlot(value: Int): Int {
        if (size == 0) return 0

        var min = 0
        var max = size - 1
        while (min <= max) {
            val middle = (min + max) / 2
            val middleId = _data[middle]

            if (middleId < value) {
                min = middle + 1
            } else if (middleId > value) {
                max = middle - 1
            } else {
                return -1
            }
        }
        if (_data[min] == value) return -1
        return min
    }

    private fun grow() {
        val newSize = _data.size * 2
        val newData = IntArray(newSize)
        System.arraycopy(_data, 0, newData, 0, _data.size)
        _data = newData
    }

    fun indexOf(value: Int): Int {
        var idx = 0
        val needle = IntVector.broadcast(ivSpecies, value)
        while (idx < size) {
            val vector = IntVector.fromArray(ivSpecies, _data, idx)
            val cr1 = vector.compare(VectorOperators.EQ, needle)

            val offset = idx
            val bits = cr1.toLong()
            if (bits != 0L) {
                val trailing = java.lang.Long.numberOfTrailingZeros(bits)
                val foundAtIndex = offset + trailing
                if (foundAtIndex >= size) return -1
                return foundAtIndex
            }

            idx += ivLength
        }
        return -1
    }

    override fun iterator(): Iterator<Int> {
        return object : Iterator<Int> {
            var ptr = 0
            override fun hasNext(): Boolean = ptr < size

            override fun next(): Int {
                return _data[ptr++]
            }
        }
    }

    companion object {
        private val ivSpecies = IntVector.SPECIES_PREFERRED
        private val ivLength = ivSpecies.length()
    }
}

package dk.sdu.cloud.app.orchestrator.services

import kotlin.time.measureTimedValue

private val globalCounters = ArrayList<EnabledPerfCounter>()

//typealias PerfCounter = EnabledPerfCounter
//typealias PerfThreadLocal<T> = ThreadLocal<T>

typealias PerfCounter = DisabledPerfCounter
typealias PerfThreadLocal<T> = DisabledThreadLocal<T>

class DisabledThreadLocal<T>(val value: T) {
    fun get() = value

    companion object {
        fun <T> withInitial(supplier: () -> T): DisabledThreadLocal<T> = DisabledThreadLocal(supplier())
    }
}

fun threadLocalCounter(name: String): PerfThreadLocal<PerfCounter> =
    PerfThreadLocal.withInitial { PerfCounter(name) }

inline fun <R> PerfThreadLocal<PerfCounter>.measureValue(iterations: Long = 1, block: () -> R): R {
    return get().measureValue(iterations, block)
}

inline fun <R> PerfCounter.measureValue(iterations: Long = 1, block: () -> R): R {
    val start = System.nanoTime()
    try {
        return block()
    } finally {
        val end = System.nanoTime()
        measure(end - start, iterations)
    }
}

@JvmInline
value class DisabledPerfCounter(val name: String) {
    @Suppress("NOTHING_TO_INLINE")
    inline fun measure(nanos: Long, iterations: Long = 1) {
    }
}

class EnabledPerfCounter(val name: String, addToGlobals: Boolean = true) {
    private var iterations = 0L
    var nanos = 0L
        private set
    private var max = Long.MIN_VALUE
    private var min = Long.MAX_VALUE
    private var samples = CyclicArray<Long>(1000)

    init {
        if (addToGlobals) {
            synchronized(globalCounters) {
                globalCounters.add(this)
            }
        }
    }

    fun measure(nanos: Long, iterations: Long = 1) {
        this.nanos += nanos
        this.iterations += iterations
        if (nanos > max) this.max = nanos
        if (nanos < min) this.min = nanos
        samples.add(nanos)
    }

    fun reduce(other: EnabledPerfCounter) {
        iterations += other.iterations
        nanos += other.nanos
        if (other.max > max) max = other.max
        if (other.min < min) min = other.min


        val newSamples = CyclicArray<Long>(other.samples.size + samples.size)
        samples.forEach { newSamples.add(it) }
        other.samples.forEach { newSamples.add(it) }

        samples = newSamples
    }

    override fun toString(): String = buildString {
        appendLine("Performance of $name")
        appendLine("---------------------------------------------")

        val samplesCopy = LongArray(samples.capacity)
        for (i in samplesCopy.indices) samplesCopy[i] = samples[i]
        samplesCopy.sort()

        appendLine("Iterations: $iterations")
        appendLine("Min: $min ns")
        appendLine("Avg: ${samplesCopy.average()} ns")
        appendLine("Max: $max ns")
        if (samplesCopy.isNotEmpty()) {
            appendLine("P10: ${samplesCopy[(samplesCopy.size * 0.10).toInt()]} ns")
            appendLine("P25: ${samplesCopy[(samplesCopy.size * 0.25).toInt()]} ns")
            appendLine("P50: ${samplesCopy[(samplesCopy.size * 0.50).toInt()]} ns")
            appendLine("P75: ${samplesCopy[(samplesCopy.size * 0.75).toInt()]} ns")
            appendLine("P99: ${samplesCopy[(samplesCopy.size * 0.99).toInt()]} ns")
        }
        appendLine()
    }

    fun median(): Long {
        val samplesCopy = LongArray(samples.capacity)
        for (i in samplesCopy.indices) samplesCopy[i] = samples[i]
        samplesCopy.sort()
        return samplesCopy[(samplesCopy.size * 0.50).toInt()]
    }
}

fun dumpCounters() {
    synchronized(globalCounters) {
        val csvBuilder = StringBuilder()
        globalCounters.groupBy { it.name }.forEach { (k, counters) ->
            val res = EnabledPerfCounter(k, addToGlobals = false)
            for (counter in counters) res.reduce(counter)
            println(res)
            csvBuilder.appendLine("$k,${res.nanos},${res.median()}")
        }
        println()
        println(csvBuilder)
    }
}


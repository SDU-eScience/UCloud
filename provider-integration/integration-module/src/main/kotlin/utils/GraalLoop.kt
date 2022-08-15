package dk.sdu.cloud.utils

/*
 * NOTE(Dan): What is this and why is it needed? Well, to be honest. When running on a normal JVM it mostly makes things
 * marginally slower by introducing function call overhead. This "abstraction" is only here to deal with the issue that
 * GraalVM with AOT compilation _really_ does not like coroutines in loops. If there are two many calls then the entire
 * thing falls apart. It is, however, fine with just a single suspending call inside the loop body. This abstraction is
 * here for when the loop body is not easily extracted into its own function.
 *
 * Usage example:
 *
 * ```kotlin
 * whileGraal({ isEverythingOkay }) {
 *   runComputation1()
 *   runComputation2()
 *   runComputation3()
 *   runComputation4()
 *   runComputation1()
 * }
 * ```
 */

class GraalLoop(private val condition: suspend () -> Boolean, private val block: suspend () -> Unit) {
    suspend fun run() {
        while (condition()) {
            block()
        }
    }
}

suspend fun whileGraal(condition: suspend () -> Boolean, block: suspend () -> Unit) {
    GraalLoop(condition, block).run()
}

suspend fun <T> Iterable<T>.forEachGraal(consumer: suspend (T) -> Unit) {
    asSequence().forEach { consumer(it) }
}

suspend fun <K, V> Map<K, V>.forEachGraal(consumer: suspend (K, V) -> Unit) {
    asSequence().forEach { (k, v) -> consumer(k, v) }
}

suspend fun BooleanArray.forEachGraal(consumer: suspend (Boolean) -> Unit) {
    asSequence().forEach { consumer(it) }
}

suspend fun BooleanArray.forEachIndexedGraal(consumer: suspend (Int, Boolean) -> Unit) {
    asSequence().forEachIndexed { idx, it -> consumer(idx, it) }
}

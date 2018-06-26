package dk.sdu.cloud.storage.util

import org.slf4j.LoggerFactory

class DebugTimer(val name: String) {
    var accumulatedTime: Long = 0L
    var iterations = 0

    inline fun <T> time(closure: () -> T): T {
        val start = System.currentTimeMillis()
        val result = closure()
        accumulatedTime += System.currentTimeMillis() - start
        iterations++
        return result
    }

    fun log() {
        if (log.isDebugEnabled && iterations > 0) {
            val avg = accumulatedTime / iterations

            log.debug("$name: took $accumulatedTime ms")
            log.debug("$name: iterations = $iterations")
            log.debug("$name: avg = $avg ms")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DebugTimer::class.java)
    }
}

fun <T> timed(name: String, closure: () -> T): T {
    return DebugTimer(name).run {
        val result = time(closure)
        log()
        result
    }
}
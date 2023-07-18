package dk.sdu.cloud.app.orchestrator.services

import kotlinx.coroutines.sync.Mutex
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// Section 4.2 of the "Little Book of Semaphores"
// https://greenteapress.com/semaphores/LittleBookOfSemaphores.pdf
class ReadWriterMutex {
    private val turnstile = Mutex()

    private var readers = 0
    private val readerMutex = Mutex()

    private val roomEmpty = Mutex()

    suspend fun Mutex.lockSpin() {
        var tries = 0
        while (tries < 10_000) {
            val a = tryLock()
            val b = tryLock()
            val c = tryLock()
            val d = tryLock()
            val e = tryLock()
            if (a || b || c || d || e) return

            tries += 5
        }
        lock()
    }

    suspend inline fun <R> Mutex.withLockSpin(block: () -> R): R {
        lockSpin()
        try {
            return block()
        } finally {
            unlock()
        }
    }

    suspend fun acquireRead() {
        turnstile.withLockSpin { /* do nothing */ }
        readerMutex.withLockSpin {
            readers++
            if (readers == 1) {
                roomEmpty.lockSpin()
            }
        }
    }

    suspend fun releaseRead() {
        readerMutex.withLockSpin {
            readers--
            if (readers == 0) {
                roomEmpty.unlock()
            }
        }
    }

    suspend fun acquireWrite() {
        turnstile.lockSpin()
        roomEmpty.lockSpin()
    }

    fun releaseWrite() {
        turnstile.unlock()
        roomEmpty.unlock()
    }

    @OptIn(ExperimentalContracts::class)
    suspend inline fun <R> withReader(block: () -> R): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        acquireRead()
        try {
            return block()
        } finally {
            releaseRead()
        }
    }

    @OptIn(ExperimentalContracts::class)
    suspend inline fun <R> withWriter(block: () -> R): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        acquireWrite()
        try {
            return block()
        } finally {
            releaseWrite()
        }
    }
}

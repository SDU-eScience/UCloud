package dk.sdu.cloud.app.orchestrator.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    suspend fun acquireRead() {
        turnstile.withLock { /* do nothing */ }
        readerMutex.withLock {
            readers++
            if (readers == 1) {
                roomEmpty.lock()
            }
        }
    }

    suspend fun releaseRead() {
        readerMutex.withLock {
            readers--
            if (readers == 0) {
                roomEmpty.unlock()
            }
        }
    }

    suspend fun acquireWrite() {
        turnstile.lock()
        roomEmpty.lock()
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

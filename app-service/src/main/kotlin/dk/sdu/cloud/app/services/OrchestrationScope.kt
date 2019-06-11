package dk.sdu.cloud.app.services

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

object OrchestrationScope : CoroutineScope {
    private lateinit var dispatcher: CoroutineDispatcher
    private var executor: ExecutorService? = null

    override val coroutineContext: CoroutineContext
        get() = dispatcher

    fun init() {
        // We use a cached thread pool which should allow for the threads to block for longer periods of time.
        // The executor will create a new thread if one is needed and none are available. If threads are available they
        // will be re-used. If they go idle they will be deleted.
        val newCachedThreadPool = Executors.newCachedThreadPool()
        executor = newCachedThreadPool
        dispatcher = newCachedThreadPool.asCoroutineDispatcher()
    }

    fun stop() {
        executor?.shutdown()
        executor = null
    }

    fun reset() {
        val executor = this.executor
        if (executor != null) {
            stop()
            while (!executor.isShutdown) {
                Thread.sleep(10)
            }
        }

        init()
    }
}

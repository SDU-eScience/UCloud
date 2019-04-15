package dk.sdu.cloud.app.services

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

object OrchestrationScope : CoroutineScope {
    private lateinit var dispatcher: CoroutineDispatcher
    private lateinit var executor: ExecutorService
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = dispatcher + job

    fun init() {
        // We use a cached thread pool which should allow for the threads to block for longer periods of time.
        // The executor will create a new thread if one is needed and none are available. If threads are available they
        // will be re-used. If they go idle they will be deleted.
        executor = Executors.newCachedThreadPool()
        dispatcher = executor.asCoroutineDispatcher()
        job = Job()
    }

    fun stop() {
        job.cancel()
        executor.shutdown()
    }

    fun reset() {
        if (this::job.isInitialized && !job.isCompleted) {
            stop()
            while (!executor.isShutdown) {
                Thread.sleep(10)
            }
        }

        init()
    }
}
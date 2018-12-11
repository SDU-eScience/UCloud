package dk.sdu.cloud.file.services

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

object BackgroundScope : CoroutineScope {
    private lateinit var dispatcher: CoroutineDispatcher
    private lateinit var executor: ExecutorService
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = dispatcher + job

    fun init() {
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

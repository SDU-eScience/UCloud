package dk.sdu.cloud.file.services.background

import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

object BackgroundScope : CoroutineScope, Loggable {
    override val log: Logger = logger()
    private lateinit var dispatcher: CoroutineDispatcher
    private lateinit var executor: ExecutorService
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = dispatcher + job

    fun init() {
        log.debug("Calling init()")
        synchronized(this) {
            if (!this::job.isInitialized || job.isCompleted) {
                log.info("Initializing BackgroundScope")
                executor = Executors.newCachedThreadPool()
                dispatcher = executor.asCoroutineDispatcher()
                job = Job()
            }
        }
    }

    fun stop() {
        log.debug("Calling stop()")
        job.cancel()
        executor.shutdown()
    }

    fun reset() {
        log.debug("Calling reset()")
        if (this::job.isInitialized && !job.isCompleted) {
            log.info("Resetting BackgroundScope")
            stop()
            while (!executor.isShutdown) {
                Thread.sleep(10)
            }
        }

        init()
    }
}

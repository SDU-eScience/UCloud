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
    private var executor: ExecutorService? = null

    override val coroutineContext: CoroutineContext
        get() = dispatcher

    fun init() {
        log.debug("Calling init()")
        synchronized(this) {
            if (executor != null) {
                log.info("Initializing BackgroundScope")
                val newCachedThreadPool = Executors.newCachedThreadPool()
                executor = newCachedThreadPool
                dispatcher = newCachedThreadPool.asCoroutineDispatcher()
            }
        }
    }

    fun stop() {
        log.debug("Calling stop()")
        executor?.shutdown()
        executor = null
    }

    fun reset() {
        log.debug("Calling reset()")
        val executor = executor
        if (executor != null) {
            log.info("Resetting BackgroundScope")
            stop()
            while (!executor.isShutdown) {
                Thread.sleep(10)
            }
        }

        init()
    }
}

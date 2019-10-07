package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

object LinuxFSScope : CoroutineScope, Closeable, Loggable {
    override val log = logger()

    private val counter = AtomicInteger(0)
    private val executor: ExecutorService by lazy {
        Executors.newCachedThreadPool { runnable ->
            Thread({
                log.debug("Creating new thread")
                StandardCLib.setfsgid(LINUX_FS_USER_UID)
                StandardCLib.setfsuid(LINUX_FS_USER_UID)

                runnable.run()
            }, "linux-fs-${counter.getAndIncrement()}")
        }
    }

    private val dispatcher: CoroutineDispatcher by lazy {
        executor.asCoroutineDispatcher()
    }

    override val coroutineContext: CoroutineContext
        get() = dispatcher

    override fun close() {
        executor.shutdown()
    }
}

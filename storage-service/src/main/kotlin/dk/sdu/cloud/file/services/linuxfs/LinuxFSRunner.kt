package dk.sdu.cloud.file.services.linuxfs

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.services.StorageUserDao
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class LinuxFSRunner(
    private val userDao: StorageUserDao<Long>,
    override val user: String
) : CommandRunner {
    private val queue = ArrayBlockingQueue<FutureTask<*>>(64)
    private var thread: Thread? = null
    private var isRunning: Boolean = false

    private fun init() {
        synchronized(this) {
            if (thread == null) {
                isRunning = true
                thread = Thread(
                    {
                        val cloudUser = runBlocking { userDao.findStorageUser(user) }
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                        StandardCLib.setfsgid(cloudUser)
                        StandardCLib.setfsuid(cloudUser)

                        while (isRunning) {
                            val nextJob = queue.poll(1, TimeUnit.SECONDS) ?: continue
                            nextJob.run()
                        }
                    },
                    THREAD_PREFIX + user + "-" + UUID.randomUUID().toString()
                ).also { it.start() }
            }
        }
    }

    fun <T> submit(job: () -> T): T {
        init()
        // TODO This will not work well when we are not blocking
        val futureTask = FutureTask(job)
        queue.put(futureTask)
        return futureTask.get()
    }

    fun requireContext() {
        if (!Thread.currentThread().name.startsWith("$THREAD_PREFIX$user-"))  {
            throw IllegalStateException("Code is running in an invalid context!")
        }
    }

    override fun close() {
        isRunning = false
    }

    companion object {
        const val THREAD_PREFIX = "linux-fs-thread-"
    }
}

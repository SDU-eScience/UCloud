package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.LastErrorException
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.throwExceptionBasedOnStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.NotLinkException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class LinuxFSRunner(
    private val uid: Int,
    override val user: String
) : CommandRunner {
    private val queue = ArrayBlockingQueue<Pair<Continuation<*>, () -> Any?>>(64)
    private var thread: NativeThread? = null
    private var isRunning: Boolean = false

    internal var inputStream: FileChannel? = null
    internal var inputSystemFile: File? = null

    internal var outputStream: OutputStream? = null
    internal var outputSystemFile: File? = null

    var lastJobNotSafe: Any? = null

    private fun init() {
        synchronized(this) {
            if (thread == null) {
                isRunning = true
                thread = NativeThread(THREAD_PREFIX + user + "-" + UUID.randomUUID().toString()) {
                    if (user != SERVICE_USER) {
                        StandardCLib.setfsgid(uid)
                        StandardCLib.setfsuid(uid)
                    }

                    while (isRunning) {
                        val (_, nextJob) = queue.poll(1, TimeUnit.SECONDS) ?: continue
                        nextJob()
                    }

                    queue.forEach { (cont, _) ->
                        cont.resumeWithException(CancellationException())
                    }
                }.also {
                    it.start()
                }
            }
        }
    }

    suspend fun <T> submit(job: suspend () -> T): T = suspendCoroutine { cont ->
        init()
        if (!isRunning) throw IllegalStateException("Runner has already been closed")

        val futureTask: () -> Unit = {
            runBlocking {
                try {
                    runAndRethrowNIOExceptions {
                        cont.resume(job())
                    }
                } catch (ex: Throwable) {
                    cont.resumeWithException(ex)
                }
            }
        }

        lastJobNotSafe = job
        queue.put(Pair(cont, futureTask))
    }

    fun requireContext() {
        if (!Thread.currentThread().name.startsWith("$THREAD_PREFIX$user-")) {
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

inline fun <T> runAndRethrowNIOExceptions(block: () -> T): T {
    return try {
        block()
    } catch (ex: FileSystemException) {
        when {
            ex is DirectoryNotEmptyException -> throw FSException.BadRequest("Directory not empty", cause = ex)

            ex is FileAlreadyExistsException -> throw FSException.AlreadyExists(cause = ex)

            ex is NoSuchFileException -> throw FSException.NotFound(cause = ex)

            ex is NotDirectoryException -> throw FSException.BadRequest("Not a directory", cause = ex)

            ex is NotLinkException -> throw FSException.BadRequest("Not a link", cause = ex)

            ex is AccessDeniedException -> throw FSException.PermissionException(cause = ex)

            ex.reason.contains("File name too long") -> throw FSException.BadRequest("File name too long")

            ex.message?.contains("Not a directory") == true -> {
                throw FSException.BadRequest("Not a directory")
            }

            else -> throw FSException.CriticalException(ex.message ?: "Internal error", cause = ex)
        }
    } catch (ex: NativeException) {
        throwExceptionBasedOnStatus(ex.statusCode, ex)
    } catch (ex: LastErrorException) {
        throwExceptionBasedOnStatus(ex.errorCode)
    }
}

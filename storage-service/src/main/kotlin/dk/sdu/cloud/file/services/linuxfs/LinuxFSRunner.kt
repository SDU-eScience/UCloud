package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.LastErrorException
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.throwExceptionBasedOnStatus
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.Logger
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class LinuxFSRunner(
    private val uid: Int,
    override val user: String
) : CommandRunner, CoroutineScope, Loggable {
    internal var inputStream: FileChannel? = null
    internal var inputSystemFile: File? = null

    internal var outputStream: OutputStream? = null
    internal var outputSystemFile: File? = null

    override val log: Logger = logger()

    private val executor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor().also {
            it.submit {
                if (user != SERVICE_USER) {
                    StandardCLib.setfsgid(uid)
                    StandardCLib.setfsuid(uid)
                }

                Thread.currentThread().name = THREAD_PREFIX + user
            }
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

    suspend fun <T> submit(job: suspend () -> T): T {
        return withContext(dispatcher) {
            runAndRethrowNIOExceptions {
                job()
            }
        }
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

            ex.reason.contains("File name too long") || ex.reason.contains("Filename too long") -> {
                throw FSException.BadRequest("File name too long")
            }

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

package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.LastErrorException
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.throwExceptionBasedOnStatus
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import java.io.File
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.*

class LinuxFSRunner(override val user: String) : CommandRunner, Loggable {
    internal var inputStream: FileChannel? = null
    internal var inputSystemFile: File? = null

    internal var outputStream: OutputStream? = null
    internal var outputSystemFile: File? = null

    override val log: Logger = logger()

    override fun close() {
        // Do nothing
    }

    suspend fun <T> submit(job: suspend () -> T): T {
        return withContext(LinuxFSScope.coroutineContext) {
            runAndRethrowNIOExceptions { job() }
        }
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

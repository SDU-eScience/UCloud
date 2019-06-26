package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.LastErrorException
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.throwExceptionBasedOnStatus
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

class LinuxFSRunner(
    override val user: String
) : CommandRunner {
    internal var inputStream: FileChannel? = null
    internal var inputSystemFile: File? = null

    internal var outputStream: OutputStream? = null
    internal var outputSystemFile: File? = null

//    fun <T> submit(job: () -> T): T {
//        return runAndRethrowNIOExceptions { job() }
//    }

    override fun close() {
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

            else -> throw FSException.CriticalException(ex.message ?: "Internal error", cause = ex)
        }
    } catch (ex: NativeException) {
        throwExceptionBasedOnStatus(ex.statusCode, ex)
    } catch (ex: LastErrorException) {
        throwExceptionBasedOnStatus(ex.errorCode)
    }
}

package dk.sdu.cloud.storage.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.api.StorageFile
import dk.sdu.cloud.storage.services.cephfs.FavoritedFile
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

interface FileSystemService {
    fun ls(
        user: String,
        path: String,
        includeImplicit: Boolean = false,
        includeFavorites: Boolean = true
    ): List<StorageFile>

    fun stat(user: String, path: String): StorageFile?

    fun mkdir(user: String, path: String)
    fun rmdir(user: String, path: String)

    fun move(user: String, path: String, newPath: String)
    fun copy(user: String, path: String, newPath: String)

    fun read(user: String, path: String): InputStream
    fun write(user: String, path: String, writer: OutputStream.() -> Unit)

    fun createFavorite(user: String, fileToFavorite: String)
    fun removeFavorite(user: String, favoriteFileToRemove: String)
    fun retrieveFavorites(user: String): List<FavoritedFile>

    fun grantRights(fromUser: String, toUser: String, path: String, rights: Set<AccessRight>)
    fun revokeRights(fromUser: String, toUser: String, path: String)

    fun createSoftSymbolicLink(user: String, linkFile: String, pointsTo: String)
    fun findFreeNameForNewFile(user: String, desiredPath: String): String

    fun homeDirectory(user: String): String

    fun joinPath(vararg components: String, isDirectory: Boolean = false): String {
        return components.joinToString("/") + (if (isDirectory) "/" else "")
    }
}

sealed class FileSystemException(override val message: String, val isCritical: Boolean = false) : RuntimeException() {
    data class BadRequest(val why: String) : FileSystemException("Bad exception")
    data class NotFound(val file: String) : FileSystemException("Not found: $file")
    data class AlreadyExists(val file: String) : FileSystemException("Already exists: $file")
    class PermissionException : FileSystemException("Permission denied")
    class CriticalException(why: String) : FileSystemException("Critical exception: $why", true)
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage>.tryWithFS(body: () -> Unit) {
    try {
        body()
    } catch (ex: Exception) {
        handleFSException(ex)
    }
}

suspend fun RESTHandler<*, *, CommonErrorMessage>.handleFSException(ex: Exception) {
    when (ex) {
        is FileSystemException -> {
            // Enforce that we must handle all cases. Will cause a compiler error if we don't cover all
            @Suppress("UNUSED_VARIABLE")
            val ignored = when (ex) {
                is FileSystemException.NotFound -> error(CommonErrorMessage(ex.message), HttpStatusCode.NotFound)
                is FileSystemException.BadRequest -> error(CommonErrorMessage(ex.message), HttpStatusCode.BadRequest)
                is FileSystemException.AlreadyExists -> error(CommonErrorMessage(ex.message), HttpStatusCode.Conflict)
                is FileSystemException.PermissionException -> error(
                    CommonErrorMessage(ex.message),
                    HttpStatusCode.Forbidden
                )
                is FileSystemException.CriticalException -> {
                    log.warn("Caught critical FS exception!")
                    log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
                }
            }
        }

        is IllegalArgumentException -> {
            error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
        }

        else -> {
            log.warn("Unknown FS exception!")
            log.warn(ex.stackTraceToString())
            error(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
        }
    }
}

private val log = LoggerFactory.getLogger(FileSystemService::class.java)

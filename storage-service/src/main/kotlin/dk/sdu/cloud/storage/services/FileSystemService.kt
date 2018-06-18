package dk.sdu.cloud.storage.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.AccessRight
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.StorageFile
import dk.sdu.cloud.storage.services.cephfs.*
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream

typealias FSUserContext = StreamingProcessRunner

interface FileSystemService {
    fun ls(
        ctx: FSUserContext,
        path: String,
        includeImplicit: Boolean = false,
        includeFavorites: Boolean = true
    ): List<StorageFile>

    fun stat(ctx: FSUserContext, path: String): StorageFile?

    fun mkdir(ctx: FSUserContext, path: String)
    fun rmdir(ctx: FSUserContext, path: String)

    fun move(ctx: FSUserContext, path: String, newPath: String)
    fun copy(ctx: FSUserContext, path: String, newPath: String)

    suspend fun <T> coRead(ctx: FSUserContext, path: String, consumer: suspend InputStream.() -> T): T
    fun <T> read(ctx: FSUserContext, path: String, consumer: InputStream.() -> T): T
    suspend fun <T> coWrite(ctx: FSUserContext, path: String, writer: suspend OutputStream.() -> T): T
    fun <T> write(ctx: FSUserContext, path: String, writer: OutputStream.() -> T): T

    fun createFavorite(ctx: FSUserContext, fileToFavorite: String)
    fun removeFavorite(ctx: FSUserContext, favoriteFileToRemove: String)
    fun retrieveFavorites(ctx: FSUserContext): List<FavoritedFile>

    fun grantRights(ctx: FSUserContext, toUser: String, path: String, rights: Set<AccessRight>)
    fun revokeRights(ctx: FSUserContext, toUser: String, path: String)

    fun createSoftSymbolicLink(ctx: FSUserContext, linkFile: String, pointsTo: String)
    fun findFreeNameForNewFile(ctx: FSUserContext, desiredPath: String): String

    fun homeDirectory(ctx: FSUserContext): String

    fun joinPath(vararg components: String, isDirectory: Boolean = false): String {
        return File(components.joinToString("/") + (if (isDirectory) "/" else "")).normalize().path
    }

    fun listMetadataKeys(ctx: FSUserContext, path: String): List<String>
    fun getMetaValue(ctx: FSUserContext, path: String, key: String): String
    fun setMetaValue(ctx: FSUserContext, path: String, key: String, value: String)

    fun annotateFiles(ctx: FSUserContext, path: String, annotation: String)

    /**
     * Retrieves a "sync" list of files starting at [path].
     *
     * Given the length of these list items are streamed through the [itemHandler].
     *
     * Only file entries that have been modified since [modifiedSince] will be included.
     */
    suspend fun tree(
        ctx: FSUserContext,
        path: String,
        mode: Set<FileAttribute>,
        itemHandler: suspend (FileRow) -> Unit
    )

    fun openContext(user: String): FSUserContext

    fun markAsOpenAccess(ctx: FSUserContext, path: String)
}

data class SyncItem(
    val type: FileType,
    val unixMode: Int,
    val user: String,
    val group: String,
    val size: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val accessedAt: Long,
    val uniqueId: String,
    val checksum: String?,
    val checksumType: String?,
    val path: String
)

sealed class FSException(override val message: String, val isCritical: Boolean = false) : RuntimeException() {
    data class BadRequest(val why: String = "") : FSException("Bad request $why")
    data class NotFound(val file: String? = null) : FSException("Not found ${file ?: ""}")
    data class AlreadyExists(val file: String? = null) : FSException("Already exists ${file ?: ""}")
    class PermissionException : FSException("Permission denied")
    class CriticalException(why: String) : FSException("Critical exception: $why", true)
    class IOException : FSException("Internal server error (IO)", true)
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage>.tryWithFS(body: () -> Unit) {
    try {
        body()
    } catch (ex: Exception) {
        fsLog.debug(ex.stackTraceToString())
        handleFSException(ex)
    }
}

suspend fun RESTHandler<*, *, CommonErrorMessage>.handleFSException(ex: Exception) {
    when (ex) {
        is FSException -> {
            // Enforce that we must handle all cases. Will cause a compiler error if we don't cover all
            @Suppress("UNUSED_VARIABLE")
            val ignored = when (ex) {
                is FSException.NotFound -> error(CommonErrorMessage(ex.message), HttpStatusCode.NotFound)
                is FSException.BadRequest -> error(CommonErrorMessage(ex.message), HttpStatusCode.BadRequest)
                is FSException.AlreadyExists -> error(CommonErrorMessage(ex.message), HttpStatusCode.Conflict)
                is FSException.PermissionException -> error(
                    CommonErrorMessage(ex.message),
                    HttpStatusCode.Forbidden
                )
                is FSException.CriticalException, is FSException.IOException -> {
                    fsLog.warn("Caught critical FS exception!")
                    fsLog.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
                }
            }
        }

        is IllegalArgumentException -> {
            error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
        }

        else -> {
            fsLog.warn("Unknown FS exception!")
            fsLog.warn(ex.stackTraceToString())
            error(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
        }
    }
}

val fsLog = LoggerFactory.getLogger(FileSystemService::class.java)

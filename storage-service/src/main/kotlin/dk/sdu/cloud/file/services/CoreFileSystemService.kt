package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.api.relativize
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.retryWithCatch
import dk.sdu.cloud.file.util.throwExceptionBasedOnStatus
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import java.io.InputStream
import java.io.OutputStream

const val XATTR_BIRTH = "birth"

class CoreFileSystemService<Ctx : FSUserContext>(
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val eventProducer: StorageEventProducer,
    private val sensitivityService: FileSensitivityService<Ctx>,
    private val cloud: AuthenticatedClient
) {
    private suspend fun writeTimeOfBirth(
        ctx: Ctx,
        path: String
    ) {
        // Note: We don't unwrap as this is expected to fail due to it already being present.
        fs.setExtendedAttribute(
            ctx,
            path,
            XATTR_BIRTH,
            (System.currentTimeMillis() / 1000).toString(),
            allowOverwrite = false
        )
    }

    suspend fun write(
        ctx: Ctx,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        writer: suspend OutputStream.() -> Unit
    ): String {
        val normalizedPath = path.normalize()
        val targetPath =
            renameAccordingToPolicy(ctx, normalizedPath, conflictPolicy)

        fs.openForWriting(ctx, targetPath, conflictPolicy.allowsOverwrite()).emitAll()
        writeTimeOfBirth(ctx, targetPath)
        fs.write(ctx, writer).emitAll()
        return targetPath
    }

    suspend fun <R> read(
        ctx: Ctx,
        path: String,
        range: LongRange? = null,
        consumer: suspend InputStream.() -> R
    ): R {
        fs.openForReading(ctx, path).unwrap()
        return fs.read(ctx, range, consumer)
    }

    suspend fun copy(
        ctx: Ctx,
        from: String,
        to: String,
        sensitivityLevel: SensitivityLevel,
        conflictPolicy: WriteConflictPolicy
    ): String {
        val normalizedFrom = from.normalize()
        val fromStat = stat(ctx, from, setOf(FileAttribute.FILE_TYPE, FileAttribute.SIZE))
        if (fromStat.fileType != FileType.DIRECTORY) {
            if (fromStat.size > 10000000000) {
                sendNotification(
                    ctx.user,
                    "Copying $from to $to.",
                    "file_copy",
                    mapOf("Destination" to to, "original" to from)
                )
            }
            val targetPath = renameAccordingToPolicy(ctx, to, conflictPolicy)
            fs.copy(ctx, from, targetPath, conflictPolicy).emitAll()
            writeTimeOfBirth(ctx, targetPath)
            setSensitivity(ctx, targetPath, sensitivityLevel)
            if (fromStat.size > 10000000000) {
                sendNotification(
                    ctx.user,
                    "Done copying $from to $to.",
                    "file_copy",
                    mapOf("Destination" to to, "original" to from)
                )
            }
            return targetPath
        } else {
            sendNotification(
                ctx.user,
                "Copying $from to $to.",
                "dir_copy",
                mapOf("Destination" to to, "original" to from)
            )
            val newRoot = renameAccordingToPolicy(ctx, to, conflictPolicy).normalize()

            if(conflictPolicy != WriteConflictPolicy.MERGE) {
                fs.makeDirectory(ctx, newRoot).emitAll()
            }

            tree(ctx, from, setOf(FileAttribute.PATH)).forEach { currentFile ->
                val currentPath = currentFile.path.normalize()
                retryWithCatch(
                    retryDelayInMs = 0L,
                    exceptionFilter = { it is FSException.AlreadyExists },
                    body = {
                        val desired = joinPath(newRoot, relativize(normalizedFrom, currentPath)).normalize()
                        if (desired == newRoot) return@forEach
                        val targetPath = renameAccordingToPolicy(ctx, desired, conflictPolicy)
                        fs.copy(ctx, currentPath, targetPath, conflictPolicy).emitAll()
                        writeTimeOfBirth(ctx, targetPath)
                    }
                )
            }
            setSensitivity(ctx, newRoot, sensitivityLevel)
            sendNotification(
                ctx.user,
                "Done copying $from to $to.",
                "dir_copy",
                mapOf("Destination" to to, "original" to from)
            )
            return newRoot
        }
    }

    suspend fun delete(
        ctx: Ctx,
        path: String
    ) {
        fs.delete(ctx, path).emitAll()
    }

    suspend fun stat(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FileRow {
        return fs.stat(ctx, path, mode).unwrap()
    }

    suspend fun statOrNull(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): FileRow? {
        return try {
            stat(ctx, path, mode)
        } catch (ex: FSException.NotFound) {
            null
        }
    }

    suspend fun listDirectory(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>,
        type: FileType? = null
    ): List<FileRow> {
        return fs.listDirectory(ctx, path, mode, type = type).unwrap()
    }

    suspend fun listDirectorySorted(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>,
        sortBy: FileSortBy,
        order: SortOrder,
        paginationRequest: NormalizedPaginationRequest? = null,
        type: FileType? = null
    ): Page<FileRow> {
        return fs.listDirectoryPaginated(
            ctx,
            path,
            mode,
            sortBy,
            paginationRequest,
            order,
            type = type
        ).unwrap()
    }

    suspend fun tree(
        ctx: Ctx,
        path: String,
        mode: Set<FileAttribute>
    ): List<FileRow> {
        return fs.tree(ctx, path, mode).unwrap()
    }

    suspend fun makeDirectory(
        ctx: Ctx,
        path: String
    ) {
        fs.makeDirectory(ctx, path).emitAll()
        writeTimeOfBirth(ctx, path)
    }

    suspend fun move(
        ctx: Ctx,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ): String {
        val targetPath = renameAccordingToPolicy(ctx, to, writeConflictPolicy)
        fs.move(ctx, from, targetPath, writeConflictPolicy).emitAll()
        return targetPath
    }

    suspend fun exists(
        ctx: Ctx,
        path: String
    ): Boolean {
        return try {
            stat(ctx, path, setOf(FileAttribute.PATH))
            true
        } catch (ex: FSException.NotFound) {
            false
        }
    }

    suspend fun renameAccordingToPolicy(
        ctx: Ctx,
        desiredTargetPath: String,
        conflictPolicy: WriteConflictPolicy
    ): String {
        if (conflictPolicy == WriteConflictPolicy.OVERWRITE) return desiredTargetPath

        // Performance: This will cause a lot of stats, on items in the same folder, for the most part we could
        // simply ls a common root and cache it. This should be a lot more efficient.
        val targetExists = exists(ctx, desiredTargetPath)
        return when (conflictPolicy) {
            WriteConflictPolicy.OVERWRITE -> desiredTargetPath

            WriteConflictPolicy.MERGE -> desiredTargetPath

            WriteConflictPolicy.RENAME -> {
                if (targetExists) findFreeNameForNewFile(ctx, desiredTargetPath)
                else desiredTargetPath
            }

            WriteConflictPolicy.REJECT -> {
                if (targetExists) throw FSException.AlreadyExists()
                else desiredTargetPath
            }
        }
    }

    private val duplicateNamingRegex = Regex("""\((\d+)\)""")
    private suspend fun findFreeNameForNewFile(ctx: Ctx, desiredPath: String): String {
        fun findFileNameNoExtension(fileName: String): String {
            return fileName.substringBefore('.')
        }

        fun findExtension(fileName: String): String {
            if (!fileName.contains(".")) return ""
            return '.' + fileName.substringAfter('.', missingDelimiterValue = "")
        }

        val fileName = desiredPath.fileName()
        val desiredWithoutExtension = findFileNameNoExtension(fileName)
        val extension = findExtension(fileName)

        val parentPath = desiredPath.parent()
        val listDirectory = listDirectory(ctx, parentPath, setOf(FileAttribute.PATH))
        val paths = listDirectory.map { it.path }
        val names = listDirectory.map { it.path.fileName() }

        return if (!paths.contains(desiredPath)) {
            desiredPath
        } else {
            val namesMappedAsIndices = names.mapNotNull {
                val nameWithoutExtension = findFileNameNoExtension(it)
                val nameWithoutPrefix = nameWithoutExtension.substringAfter(desiredWithoutExtension)
                val myExtension = findExtension(it)

                if (extension != myExtension) return@mapNotNull null

                if (nameWithoutPrefix.isEmpty()) {
                    0 // We have an exact match on the file name
                } else {
                    val match = duplicateNamingRegex.matchEntire(nameWithoutPrefix)
                    if (match == null) {
                        null // The file name doesn't match at all, i.e., the file doesn't collide with our desired name
                    } else {
                        match.groupValues.getOrNull(1)?.toIntOrNull()
                    }
                }
            }

            if (namesMappedAsIndices.isEmpty()) {
                desiredPath
            } else {
                val currentMax = namesMappedAsIndices.max() ?: 0
                "$parentPath/$desiredWithoutExtension(${currentMax + 1})$extension"
            }
        }
    }

    private suspend fun setSensitivity(ctx: Ctx, targetPath: String, sensitivityLevel: SensitivityLevel) {
        val newSensitivity = stat(ctx, targetPath, setOf(FileAttribute.SENSITIVITY))
        if (sensitivityLevel != newSensitivity.sensitivityLevel) {
            sensitivityService.setSensitivityLevel(
                ctx,
                targetPath,
                sensitivityLevel,
                ctx.user
            )
        }
    }

    private suspend fun sendNotification(user: String, message: String, type: String, notificationMeta: Map<String,Any>) {
        NotificationDescriptions.create.call(
            CreateNotification(
                user,
                Notification(
                    type,
                    message,
                    meta = notificationMeta
                )
            ),
            cloud
        )
    }

    private fun <T : StorageEvent> FSResult<List<T>>.emitAll(): List<T> {
        val result = unwrap()

        log.debug("Emitting storage ${result.size} events: ${result.take(5)}")
        eventProducer.produceInBackground(result)
        return result
    }

    private fun <T> FSResult<T>.unwrap(): T {
        if (statusCode != 0) {
            throwExceptionBasedOnStatus(statusCode)
        } else {
            return value
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

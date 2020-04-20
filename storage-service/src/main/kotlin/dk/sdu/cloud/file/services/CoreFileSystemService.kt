package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.MetadataService
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.retryWithCatch
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.task.api.MeasuredSpeedInteger
import dk.sdu.cloud.task.api.Progress
import dk.sdu.cloud.task.api.runTask
import java.io.InputStream
import java.io.OutputStream

class CoreFileSystemService<Ctx : FSUserContext>(
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val wsServiceClient: AuthenticatedClient,
    private val backgroundScope: BackgroundScope,
    private val metadataService: MetadataService
) {
    suspend fun write(
        ctx: Ctx,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        writer: suspend OutputStream.() -> Unit
    ): String {
        val normalizedPath = path.normalize()
        val targetPath =
            renameAccordingToPolicy(ctx, normalizedPath, conflictPolicy)

        fs.openForWriting(ctx, targetPath, conflictPolicy.allowsOverwrite())
        fs.write(ctx, writer)
        return targetPath
    }

    suspend fun <R> read(
        ctx: Ctx,
        path: String,
        range: LongRange? = null,
        consumer: suspend InputStream.() -> R
    ): R {
        fs.openForReading(ctx, path)
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
        val fromStat = stat(ctx, from, setOf(StorageFileAttribute.fileType, StorageFileAttribute.size))
        if (fromStat.fileType != FileType.DIRECTORY) {
            runTask(wsServiceClient, backgroundScope, "File copy", ctx.user) {
                status = "Copying file from '$from' to '$to'"

                val targetPath = renameAccordingToPolicy(ctx, to, conflictPolicy)
                fs.copy(ctx, from, targetPath, conflictPolicy, this)
                setSensitivityLevel(ctx, targetPath, sensitivityLevel)

                return targetPath
            }
        } else {
            runTask(wsServiceClient, backgroundScope, "File copy", ctx.user) {
                status = "Copying files from '$from' to '$to'"
                val filesPerSecond = MeasuredSpeedInteger("Files copied per second", "Files/s")
                this.speeds = listOf(filesPerSecond)

                val newRoot = renameAccordingToPolicy(ctx, to, conflictPolicy).normalize()
                status = "Copying files from '$from' to '$newRoot'"
                if (!exists(ctx, newRoot)) {
                    makeDirectory(ctx, newRoot)
                }

                val tree = tree(ctx, from, setOf(StorageFileAttribute.path, StorageFileAttribute.size))
                val progress = Progress("Number of files", 0, tree.size)
                this.progress = progress

                tree.forEach { currentFile ->
                    val currentPath = currentFile.path.normalize()
                    val relativeFile = relativize(normalizedFrom, currentPath)

                    writeln("Copying file '$relativeFile' (${currentFile.size} bytes)")
                    retryWithCatch(
                        retryDelayInMs = 0L,
                        exceptionFilter = { it is FSException.AlreadyExists },
                        body = {
                            val desired = joinPath(newRoot, relativeFile).normalize()
                            if (desired == newRoot) return@forEach
                            val targetPath = renameAccordingToPolicy(ctx, desired, conflictPolicy)
                            fs.copy(ctx, currentPath, targetPath, conflictPolicy, this)
                        }
                    )

                    progress.current++
                    filesPerSecond.increment(1)
                }

                setSensitivityLevel(ctx, newRoot, sensitivityLevel)
                return newRoot
            }
        }
    }

    suspend fun delete(
        ctx: Ctx,
        path: String
    ) {
        // Require permission up-front to ensure metadata is not updated without permissions
        fs.requirePermission(ctx, path, AccessRight.WRITE)
        metadataService.runDeleteAction(listOf(path)) {
            fs.delete(ctx, path)
        }
    }

    suspend fun stat(
        ctx: Ctx,
        path: String,
        mode: Set<StorageFileAttribute>
    ): StorageFile {
        return fs.stat(ctx, path, mode)
    }

    suspend fun statOrNull(
        ctx: Ctx,
        path: String,
        mode: Set<StorageFileAttribute>
    ): StorageFile? {
        return try {
            stat(ctx, path, mode)
        } catch (ex: FSException.NotFound) {
            null
        }
    }

    suspend fun listDirectory(
        ctx: Ctx,
        path: String,
        mode: Set<StorageFileAttribute>,
        type: FileType? = null
    ): List<StorageFile> {
        return fs.listDirectory(ctx, path, mode, type = type)
    }

    suspend fun listDirectorySorted(
        ctx: Ctx,
        path: String,
        mode: Set<StorageFileAttribute>,
        sortBy: FileSortBy,
        order: SortOrder,
        paginationRequest: NormalizedPaginationRequest? = null,
        type: FileType? = null
    ): Page<StorageFile> {
        return fs.listDirectoryPaginated(
            ctx,
            path,
            mode,
            sortBy,
            paginationRequest,
            order,
            type = type
        )
    }

    suspend fun tree(
        ctx: Ctx,
        path: String,
        mode: Set<StorageFileAttribute>
    ): List<StorageFile> {
        return fs.tree(ctx, path, mode)
    }

    suspend fun makeDirectory(
        ctx: Ctx,
        path: String
    ) {
        fs.makeDirectory(ctx, path)
    }

    suspend fun move(
        ctx: Ctx,
        from: String,
        to: String,
        writeConflictPolicy: WriteConflictPolicy
    ): String {
        val targetPath = renameAccordingToPolicy(ctx, to, writeConflictPolicy)
        fs.requirePermission(ctx, from.normalize(), AccessRight.WRITE)
        fs.requirePermission(ctx, targetPath.normalize(), AccessRight.WRITE)

        if (from.normalize().startsWith("/home/") && to.normalize().startsWith("/projects/")) {
            // We must remove all shares here
            // TODO This probably isn't the best place for this code
            metadataService.removeAllMetadataOfType(
                from.normalize(),
                AclService.USER_METADATA_TYPE
            )
        }

        metadataService.runMoveAction(from.normalize(), targetPath.normalize()) {
            fs.move(ctx, from, targetPath, writeConflictPolicy)
        }
        return targetPath
    }

    suspend fun exists(
        ctx: Ctx,
        path: String
    ): Boolean {
        return try {
            stat(ctx, path, setOf(StorageFileAttribute.path))
            true
        } catch (ex: FSException.NotFound) {
            false
        }
    }

    suspend fun normalizePermissions(ctx: Ctx, path: String) {
        fs.normalizePermissions(ctx, path)
        fs.tree(ctx, path, setOf(StorageFileAttribute.path)).forEach { file ->
            fs.normalizePermissions(ctx, file.path)
        }
    }

    suspend fun setSensitivityLevel(
        ctx: Ctx,
        path: String,
        sensitivityLevel: SensitivityLevel?
    ) {
        fs.setSensitivityLevel(ctx, path, sensitivityLevel)
    }

    suspend fun getSensitivityLevel(
        ctx: Ctx,
        path: String
    ): SensitivityLevel? {
        return fs.getSensitivityLevel(ctx, path)
    }

    private suspend fun renameAccordingToPolicy(
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
        val listDirectory = listDirectory(ctx, parentPath, setOf(StorageFileAttribute.path))
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

    companion object : Loggable {
        override val log = logger()
    }
}

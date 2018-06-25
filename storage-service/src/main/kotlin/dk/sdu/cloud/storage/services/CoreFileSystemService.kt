package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.services.cephfs.FileAttribute
import dk.sdu.cloud.storage.services.cephfs.FileRow
import dk.sdu.cloud.storage.util.*
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

class CoreFileSystemService(
    private val fs: LowLevelFileSystemInterface,
    private val eventProducer: StorageEventProducer
) {
    fun <R> write(
        ctx: FSUserContext,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        writer: OutputStream.() -> R
    ): R {
        val normalizedPath = path.normalize()
        val targetPath =
            renameAccordingToPolicy(ctx, normalizedPath, conflictPolicy)

        fs.openForWriting(ctx, targetPath, conflictPolicy.allowsOverwrite()).emitAll()

        return fs.write(ctx, writer)
    }

    fun <R> read(
        ctx: FSUserContext,
        path: String,
        range: IntRange? = null,
        consumer: InputStream.() -> R
    ): R {
        fs.openForReading(ctx, path).unwrap()
        return fs.read(ctx, range, consumer)
    }

    fun copy(
        ctx: FSUserContext,
        from: String,
        to: String,
        conflictPolicy: WriteConflictPolicy
    ) {
        val normalizedFrom = from.normalize()
        val fromStat = stat(ctx, from)
        if (fromStat.type != FileType.DIRECTORY) {
            val targetPath = renameAccordingToPolicy(ctx, to, conflictPolicy)
            fs.copy(ctx, from, targetPath, conflictPolicy.allowsOverwrite()).emitAll()
        } else {
            tree(ctx, from, setOf(FileAttribute.PATH)).forEach {
                val currentPath = it.path

                retryWithCatch(
                    retryDelayInMs = 0L,
                    exceptionFilter = { it is FSException.AlreadyExists },
                    body = {
                        val desired = joinPath(to.normalize(), relativize(normalizedFrom, currentPath))
                        val targetPath = renameAccordingToPolicy(ctx, desired, conflictPolicy)

                        fs.copy(ctx, currentPath, targetPath, conflictPolicy.allowsOverwrite()).emitAll()
                    }
                )
            }
        }
    }

    fun delete(
        ctx: FSUserContext,
        path: String
    ) {
        fs.delete(ctx, path).emitAll()
    }

    fun stat(
        ctx: FSUserContext,
        path: String
    ): StorageFile {
        val favorites = retrieveFavoriteInodeSet(ctx)
        return readStorageFile(
            fs.stat(ctx, path, STORAGE_FILE_ATTRIBUTES).unwrap(),
            favorites
        )
    }

    fun statOrNull(
        ctx: FSUserContext,
        path: String
    ): StorageFile? {
        return try {
            stat(ctx, path)
        } catch (ex: FSException.AlreadyExists) {
            null
        }
    }

    fun listDirectory(
        ctx: FSUserContext,
        path: String
    ): List<StorageFile> {
        val favorites = retrieveFavoriteInodeSet(ctx)
        return fs.listDirectory(ctx, path, STORAGE_FILE_ATTRIBUTES).unwrap().map { readStorageFile(it, favorites) }
    }

    private fun retrieveFavoriteInodeSet(ctx: FSUserContext): Set<String> =
        retrieveFavorites(ctx).map { it.inode }.toSet()

    fun retrieveFavorites(ctx: FSUserContext): List<FavoritedFile> {
        return fs.listDirectory(
            ctx, favoritesDirectory(ctx), setOf(
                FileAttribute.FILE_TYPE,
                FileAttribute.PATH,
                FileAttribute.LINK_TARGET,
                FileAttribute.LINK_INODE,
                FileAttribute.INODE
            )
        ).unwrap().map {
            FavoritedFile(
                type = it.fileType,
                from = it.path,
                to = it.linkTarget,
                inode = it.linkInode,
                favInode = it.inode
            )
        }
    }

    fun markAsFavorite(ctx: FSUserContext, fileToFavorite: String) {
        val favoritesDirectory = favoritesDirectory(ctx)
        if (!exists(ctx, favoritesDirectory)) {
            makeDirectory(ctx, favoritesDirectory)
        }

        createSymbolicLink(ctx, fileToFavorite, joinPath(favoritesDirectory, fileToFavorite.fileName()))
    }

    fun removeFavorite(ctx: FSUserContext, favoriteFileToRemove: String) {
        val stat = stat(ctx, favoriteFileToRemove)
        val allFavorites = retrieveFavorites(ctx)
        val toRemove = allFavorites.filter { it.inode == stat.inode || it.favInode == stat.inode }
        if (toRemove.isEmpty()) return
        toRemove.forEach { delete(ctx, it.from) }
    }

    fun tree(
        ctx: FSUserContext,
        path: String,
        mode: Set<FileAttribute>
    ): List<FileRow> {
        return fs.tree(ctx, path, mode).unwrap()
    }

    fun makeDirectory(
        ctx: FSUserContext,
        path: String
    ) {
        fs.makeDirectory(ctx, path).emitAll()
    }

    fun move(
        ctx: FSUserContext,
        from: String,
        to: String,
        conflictPolicy: WriteConflictPolicy
    ) {
        val targetPath = renameAccordingToPolicy(ctx, to, conflictPolicy)
        fs.move(ctx, from, targetPath, conflictPolicy.allowsOverwrite()).emitAll()
    }

    fun exists(
        ctx: FSUserContext,
        path: String
    ): Boolean {
        return try {
            stat(ctx, path)
            true
        } catch (ex: FSException.NotFound) {
            false
        }
    }

    fun renameAccordingToPolicy(
        ctx: FSUserContext,
        desiredTargetPath: String,
        conflictPolicy: WriteConflictPolicy
    ): String {
        if (conflictPolicy == WriteConflictPolicy.OVERWRITE) return desiredTargetPath

        // Performance: This will cause a lot of stats, on items in the same folder, for the most part we could
        // simply ls a common root and cache it. This should be a lot more efficient.
        val targetExists = exists(ctx, desiredTargetPath)
        return when (conflictPolicy) {
            WriteConflictPolicy.OVERWRITE -> desiredTargetPath

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

    fun createSymbolicLink(
        ctx: FSUserContext,
        targetPath: String,
        linkPath: String
    ) {
        // TODO Automatic renaming... Not a good idea
        val targetLocation = findFreeNameForNewFile(ctx, linkPath)
        fs.createSymbolicLink(ctx, targetPath, targetLocation).unwrap()
    }

    private val duplicateNamingRegex = Regex("""\((\d+)\)""")
    private fun findFreeNameForNewFile(ctx: FSUserContext, desiredPath: String): String {
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

        val parentPath = desiredPath.substringBeforeLast('/')
        val names = listDirectory(ctx, parentPath).map { it.path.fileName() }

        return if (names.isEmpty()) {
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

    private val eventBroadcastChannel = BroadcastChannel<StorageEvent>(512)

    suspend fun attachListener(listener: FileSystemListener) {
        listener.attachToFSChannel(eventBroadcastChannel.openSubscription())
    }

    private fun <T : StorageEvent> FSResult<List<T>>.emitAll() {
        unwrap().forEach { event ->
            launch { eventBroadcastChannel.send(event) }
            launch { eventProducer.emit(event) }
        }
    }

    private fun <T> FSResult<T>.unwrap(): T {
        if (statusCode != 0) {
            throwExceptionBasedOnStatus(statusCode)
        } else {
            return value
        }
    }

    private fun readStorageFile(row: FileRow, favorites: Set<String>): StorageFile =
        StorageFile(
            type = row.fileType,
            path = row.path,
            createdAt = row.timestamps.created,
            modifiedAt = row.timestamps.modified,
            ownerName = row.owner,
            size = row.size,
            acl = row.shares,
            favorited = row.inode in favorites,
            sensitivityLevel = row.sensitivityLevel,
            link = row.isLink,
            annotations = row.annotations,
            inode = row.inode
        )


    companion object {
        private val log = LoggerFactory.getLogger(CoreFileSystemService::class.java)

        private val STORAGE_FILE_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.PATH,
            FileAttribute.TIMESTAMPS,
            FileAttribute.OWNER,
            FileAttribute.SIZE,
            FileAttribute.SHARES,
            FileAttribute.SENSITIVITY,
            FileAttribute.ANNOTATIONS,
            FileAttribute.INODE,
            FileAttribute.IS_LINK
        )
    }
}
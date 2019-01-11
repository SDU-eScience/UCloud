package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.util.CappedInputStream
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import org.slf4j.Logger
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.reflect.KClass

sealed class BulkUploader<Ctx : FSUserContext>(val format: String, val ctxType: KClass<Ctx>) {
    abstract suspend fun upload(
        fs: CoreFileSystemService<Ctx>,
        contextFactory: () -> Ctx,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        stream: InputStream
    ): List<String>

    companion object {
        @PublishedApi
        internal val instances: List<BulkUploader<*>> by lazy {
            BulkUploader::class.sealedSubclasses.mapNotNull { it.objectInstance }
        }

        fun <Ctx : FSUserContext> fromFormat(format: String, klass: KClass<Ctx>): BulkUploader<Ctx>? {
            @Suppress("UNCHECKED_CAST")
            return instances.find { it.format == format && klass == it.ctxType } as? BulkUploader<Ctx>
        }
    }
}

@Suppress("unused")
object ZipBulkUploader : BulkUploader<UnixFSCommandRunner>("zip", UnixFSCommandRunner::class), Loggable {
    override val log = logger()

    override suspend fun upload(
        fs: CoreFileSystemService<UnixFSCommandRunner>,
        contextFactory: () -> UnixFSCommandRunner,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        stream: InputStream
    ): List<String> {
        return BasicUploader.uploadFromSequence(fs, contextFactory, conflictPolicy, sequence {
            yield(ArchiveEntry.Directory(path))

            ZipInputStream(stream).use { zipStream ->
                var entry: ZipEntry? = zipStream.nextEntry
                while (entry != null) {
                    val initialTargetPath = joinPath(path, entry.name)
                    if (entry.name.contains("__MACOSX")) {
                        log.debug("Skipping Entry: " + entry.name)
                        entry = zipStream.nextEntry
                    } else {
                        if (entry.isDirectory) {
                            yield(ArchiveEntry.Directory(initialTargetPath))
                        } else {
                            yield(ArchiveEntry.File(
                                path = initialTargetPath,
                                stream = zipStream,
                                dispose = { zipStream.closeEntry() }
                            ))
                        }
                        entry = zipStream.nextEntry
                    }
                }
            }
        })
    }
}

@Suppress("unused")
object TarGzUploader : BulkUploader<UnixFSCommandRunner>("tgz", UnixFSCommandRunner::class), Loggable {
    override val log: Logger = logger()

    override suspend fun upload(
        fs: CoreFileSystemService<UnixFSCommandRunner>,
        contextFactory: () -> UnixFSCommandRunner,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        stream: InputStream
    ): List<String> {
        return BasicUploader.uploadFromSequence(fs, contextFactory, conflictPolicy, sequence {
            TarInputStream(GZIPInputStream(stream)).use {
                var entry: TarEntry? = it.nextEntry
                while (entry != null) {
                    val initialTargetPath = joinPath(path, entry.name)
                    val cappedStream = CappedInputStream(it, entry.size)
                    if (entry.name.contains("PaxHeader/")) {
                        // This is some meta data stuff in the tarball. We don't want this
                        log.debug("Skipping entry: ${entry.name}")
                        cappedStream.skipRemaining()
                    } else {
                        if (entry.isDirectory) {
                            yield(ArchiveEntry.Directory(initialTargetPath))
                        } else {
                            yield(
                                ArchiveEntry.File(
                                    path = initialTargetPath,
                                    stream = cappedStream,
                                    dispose = { cappedStream.skipRemaining() }
                                )
                            )
                        }
                    }

                    entry = it.nextEntry
                }
            }
        })
    }
}

sealed class ArchiveEntry {
    abstract val path: String

    data class File(
        override val path: String,
        val stream: InputStream,
        val dispose: () -> Unit
    ) : ArchiveEntry()

    data class Directory(override val path: String) : ArchiveEntry()
}

private object BasicUploader : Loggable {
    override val log = logger()

    suspend fun <Ctx : FSUserContext> uploadFromSequence(
        fs: CoreFileSystemService<Ctx>,
        contextFactory: () -> Ctx,
        conflictPolicy: WriteConflictPolicy,
        sequence: Sequence<ArchiveEntry>
    ): List<String> {
        val rejectedFiles = ArrayList<String>()
        val rejectedDirectories = ArrayList<String>()
        val createdDirectories = HashSet<String>()

        var ctx = contextFactory()

        try {
            sequence.forEach { entry ->
                try {
                    log.debug("New entry $entry")
                    if (rejectedDirectories.any { entry.path.startsWith(it) }) {
                        log.debug("Skipping entry: $entry")
                        rejectedFiles += entry.path
                        return@forEach
                    }

                    log.debug("Downloading $entry")

                    val existing = fs.statOrNull(ctx, entry.path, setOf(FileAttribute.FILE_TYPE))

                    val targetPath: String? = if (existing != null) {
                        // TODO This is technically handled by upload also
                        val existingIsDirectory = existing.fileType == FileType.DIRECTORY
                        if (entry is ArchiveEntry.Directory != existingIsDirectory) {
                            log.debug("Type of existing and new does not match. Rejecting regardless of policy")
                            rejectedDirectories += entry.path
                            null
                        } else {
                            if (entry is ArchiveEntry.Directory) {
                                log.debug("Directory already exists. Skipping")
                                null
                            } else {
                                entry.path // Renaming/rejection handled by upload
                            }
                        }
                    } else {
                        log.debug("File does not exist")
                        entry.path
                    }

                    if (targetPath != null) {
                        log.debug("Accepting file ${entry.path} ($targetPath)")

                        try {
                            when (entry) {
                                is ArchiveEntry.Directory -> {
                                    createdDirectories += targetPath
                                    fs.makeDirectory(ctx, targetPath)
                                }

                                is ArchiveEntry.File -> {
                                    val parentDir = File(targetPath).parentFile.path
                                    if (parentDir !in createdDirectories) {
                                        createdDirectories += parentDir
                                        try {
                                            fs.makeDirectory(ctx, parentDir)
                                        } catch (ex: FSException.AlreadyExists) {
                                            log.debug("Parent directory already exists")
                                        }
                                    }

                                    fs.write(ctx, targetPath, conflictPolicy) { entry.stream.copyTo(this) }
                                }
                            }
                        } catch (ex: FSException.PermissionException) {
                            log.debug("Skipping $entry because of permissions")
                            rejectedFiles += entry.path
                        }
                    } else {
                        log.debug("Skipping $entry because we could not rename")
                        rejectedFiles += entry.path
                    }
                } catch (ex: Exception) {
                    log.warn("Caught exception while extracting archive!")
                    log.warn(ex.stackTraceToString())
                    runCatching { ctx.close() }

                    ctx = contextFactory()
                } finally {
                    if (entry is ArchiveEntry.File) {
                        entry.dispose()
                    }
                }
            }
        } finally {
            runCatching { ctx.close() }
        }

        return rejectedFiles
    }
}

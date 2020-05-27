package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.BasicUploader.log
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.util.CappedInputStream
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.task.api.MeasuredSpeedInteger
import dk.sdu.cloud.task.api.runTask
import kotlinx.coroutines.runBlocking
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import org.slf4j.Logger
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.reflect.KClass

sealed class BulkUploader<Ctx : FSUserContext>(val format: String, val ctxType: KClass<Ctx>) {
    abstract suspend fun upload(
        serviceCloud: AuthenticatedClient,
        fs: CoreFileSystemService<Ctx>,
        contextFactory: suspend () -> Ctx,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        stream: InputStream,
        sensitivity: SensitivityLevel?,
        archiveName: String,
        backgroundScope: BackgroundScope
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
object ZipBulkUploader : BulkUploader<LinuxFSRunner>("zip", LinuxFSRunner::class), Loggable {
    override val log = logger()

    override suspend fun upload(
        serviceCloud: AuthenticatedClient,
        fs: CoreFileSystemService<LinuxFSRunner>,
        contextFactory: suspend () -> LinuxFSRunner,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        stream: InputStream,
        sensitivity: SensitivityLevel?,
        archiveName: String,
        backgroundScope: BackgroundScope
    ): List<String> {
        return BasicUploader.uploadFromSequence(
            serviceCloud,
            path,
            fs,
            contextFactory,
            conflictPolicy,
            sensitivity,
            archiveName,
            backgroundScope,
            sequence {
                yield(ArchiveEntry.Directory(path))

                ZipInputStream(stream).use { zipStream ->
                    var entry: ZipEntry? = zipStream.nextEntry
                    while (entry != null) {
                        val initialTargetPath = joinPath(path, entry.name)
                        if (entry.name.contains("__MACOSX") ||
                            entry.name.contains(".DS_Store")
                        ) {
                            log.debug("Skipping Entry: " + entry.name)
                            entry = zipStream.nextEntry
                        } else {
                            yieldDirectoriesUntilTarget(initialTargetPath)

                            if (entry.isDirectory) {
                                yield(ArchiveEntry.Directory(initialTargetPath))
                            } else {
                                yield(ArchiveEntry.File(
                                    path = initialTargetPath,
                                    stream = zipStream,
                                    dispose = {zipStream.closeEntry()}
                                ))
                            }
                            entry = zipStream.nextEntry
                        }
                    }
                }
            })
    }
}

private suspend fun SequenceScope<ArchiveEntry>.yieldDirectoriesUntilTarget(
    initialTargetPath: String
) {
    val allComponents = initialTargetPath.components().dropLast(1)
    val paths = (3..allComponents.size).map { i ->
        joinPath(*allComponents.take(i).toTypedArray())
    }
    paths.forEach {
        yield(ArchiveEntry.Directory(it))
    }
}

@Suppress("unused")
object TarGzUploader : BulkUploader<LinuxFSRunner>("tgz", LinuxFSRunner::class), Loggable {
    override val log: Logger = logger()

    override suspend fun upload(
        serviceCloud: AuthenticatedClient,
        fs: CoreFileSystemService<LinuxFSRunner>,
        contextFactory: suspend () -> LinuxFSRunner,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        stream: InputStream,
        sensitivity: SensitivityLevel?,
        archiveName: String,
        backgroundScope: BackgroundScope
    ): List<String> {
        return BasicUploader.uploadFromSequence(
            serviceCloud,
            path,
            fs,
            contextFactory,
            conflictPolicy,
            sensitivity,
            archiveName,
            backgroundScope,
            sequence {
                TarInputStream(GZIPInputStream(stream)).use {
                    var entry: TarEntry? = it.nextEntry
                    while (entry != null) {
                        val initialTargetPath = joinPath(path, entry.name)
                        val cappedStream = CappedInputStream(it, entry.size)
                        if (entry.name.contains("PaxHeader/") ||
                            entry.name.contains("/._") ||
                            entry.name.contains(".DS_Store")
                        ) {
                            // This is some meta data stuff in the tarball. We don't want this
                            log.debug("Skipping entry: ${entry.name}")
                            cappedStream.skipRemaining()
                        } else {
                            yieldDirectoriesUntilTarget(initialTargetPath)

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
            }
        )
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
        serviceCloud: AuthenticatedClient,
        path: String,
        fs: CoreFileSystemService<Ctx>,
        contextFactory: suspend () -> Ctx,
        conflictPolicy: WriteConflictPolicy,
        sensitivity: SensitivityLevel?,
        archiveName: String,
        backgroundScope: BackgroundScope,
        sequence: Sequence<ArchiveEntry>
    ): List<String> {
        val rejectedFiles = ArrayList<String>()
        val rejectedDirectories = ArrayList<String>()
        val createdDirectories = HashSet<String>()

        var ctx = contextFactory()

        try {
            runTask(serviceCloud, backgroundScope, "File extraction", ctx.user) {
                status = "Extracting '$archiveName'"
                val bytesPerSecond = MeasuredSpeedInteger("Transfer speed", "B/s") { speed ->
                    bytesToString(speed) + "/s"
                }

                val filesPerSecond = MeasuredSpeedInteger("Files per second", "Files/s")

                this.speeds = listOf(bytesPerSecond, filesPerSecond)
                this.progress = null // we have no idea of when the file stream ends

                sequence.forEach { entry ->
                    log.debug("New Entry: $entry}")
                    try {
                        if (rejectedDirectories.any { entry.path.startsWith(it) }) {
                            log.debug("Skipping entry: $entry")
                            rejectedFiles += entry.path
                            return@forEach
                        }

                        if (entry is ArchiveEntry.Directory && entry.path in createdDirectories) {
                            return@forEach
                        }

                        val existing = fs.statOrNull(ctx, entry.path, setOf(StorageFileAttribute.fileType))

                        writeln("Extracting file to ${entry.path}")

                        val targetPath: String? = if (existing != null) {
                            // TODO This is technically handled by upload also
                            val existingIsDirectory = existing.fileType == FileType.DIRECTORY
                            if (entry is ArchiveEntry.Directory != existingIsDirectory) {
                                rejectedDirectories += entry.path
                                null
                            } else {
                                if (entry is ArchiveEntry.Directory) {
                                    null
                                } else {
                                    entry.path // Renaming/rejection handled by upload
                                }
                            }
                        } else {
                            entry.path
                        }

                        if (targetPath != null) {
                            try {
                                when (entry) {
                                    is ArchiveEntry.Directory -> {
                                        createdDirectories += targetPath
                                        fs.makeDirectory(ctx, targetPath)
                                        if (sensitivity != null) {
                                            fs.setSensitivityLevel(ctx, targetPath, sensitivity)
                                        }
                                    }

                                    is ArchiveEntry.File -> {
                                        try {
                                            val newFile = fs.write(ctx, targetPath, conflictPolicy) {
                                                entry.stream.copyToWithTracking(
                                                    bytesPerSecond,
                                                    this
                                                )
                                            }

                                            if (sensitivity != null) {
                                                fs.setSensitivityLevel(ctx, newFile, sensitivity)
                                            }
                                        } catch (ex: ZipException) {
                                            NotificationDescriptions.create.call(
                                                CreateNotification(
                                                    targetPath.split("/")[2],
                                                    Notification(
                                                        "EXTRACT_FAILED",
                                                        "Extraction failed.\n " +
                                                                "This might be due to the zip file was created " +
                                                                "on mac using compress or just corrupted. " +
                                                                "Try recreate the file using zip or " +
                                                                "tar from terminal"
                                                    )
                                                ),
                                                serviceCloud
                                            )
                                            throw ex
                                        }
                                    }
                                }

                                filesPerSecond.increment(1)
                            } catch (ex: FSException.PermissionException) {
                                log.debug("Skipping $entry because of permissions")
                                writeln("${entry.path} was rejected due to missing permissions")
                                rejectedFiles += entry.path
                            }
                        } else {
                            log.debug("Skipping $entry because we could not rename")
                            writeln("${entry.path} was rejected as we could not rename the file")
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
            }
        } finally {
            runCatching { ctx.close() }
        }
        NotificationDescriptions.create.call(
            CreateNotification(
                path.split("/")[2],
                Notification(
                    "EXTRACTION_SUCCESS",
                    "Extraction completed"
                )
            ),
            serviceCloud
        )
        return rejectedFiles
    }
}

fun InputStream.copyToWithTracking(
    speed: MeasuredSpeedInteger,
    out: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer)
        if (bytes > 0) speed.increment(bytes.toLong())
    }
    return bytesCopied
}

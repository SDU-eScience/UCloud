package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.util.CappedInputStream
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
        serviceCloud: AuthenticatedClient,
        fs: CoreFileSystemService<Ctx>,
        contextFactory: suspend () -> Ctx,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        stream: InputStream,
        sensitivity: SensitivityLevel?,
        sensitivityService: FileSensitivityService<Ctx>,
        archiveName: String
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
        sensitivityService: FileSensitivityService<LinuxFSRunner>,
        archiveName: String
    ): List<String> {
        return BasicUploader.uploadFromSequence(
            serviceCloud,
            path,
            fs,
            contextFactory,
            conflictPolicy,
            sensitivity,
            sensitivityService,
            archiveName,
            sequence {
                yield(ArchiveEntry.Directory(path))

                ZipInputStream(stream).use { zipStream ->
                    var entry: ZipEntry? = zipStream.nextEntry
                    while (entry != null) {
                        val initialTargetPath = joinPath(path, entry.name)
                        if (entry.name.contains("__MACOSX")) {
                            log.debug("Skipping Entry: " + entry.name)
                            entry = zipStream.nextEntry
                        } else {
                            val allComponents = initialTargetPath.components().dropLast(1)
                            val paths = (3..allComponents.size).map { i ->
                                joinPath(*allComponents.take(i).toTypedArray())
                            }
                            paths.forEach {
                                yield(ArchiveEntry.Directory(it))
                            }

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
        sensitivityService: FileSensitivityService<LinuxFSRunner>,
        archiveName: String
    ): List<String> {
        return BasicUploader.uploadFromSequence(
            serviceCloud,
            path,
            fs,
            contextFactory,
            conflictPolicy,
            sensitivity,
            sensitivityService,
            archiveName,
            sequence {
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

private const val NOTIFICATION_EXTRACTION_TYPE = "file_extraction"
private const val NOTIFICATION_COOLDOWN_PERIOD = 1000 * 60 * 5L

private object BasicUploader : Loggable {
    override val log = logger()

    suspend fun <Ctx : FSUserContext> uploadFromSequence(
        serviceCloud: AuthenticatedClient,
        path: String,
        fs: CoreFileSystemService<Ctx>,
        contextFactory: suspend () -> Ctx,
        conflictPolicy: WriteConflictPolicy,
        sensitivity: SensitivityLevel?,
        sensitivityService: FileSensitivityService<Ctx>,
        archiveName: String,
        sequence: Sequence<ArchiveEntry>
    ): List<String> {
        val rejectedFiles = ArrayList<String>()
        val rejectedDirectories = ArrayList<String>()
        val createdDirectories = HashSet<String>()

        var ctx = contextFactory()

        var nextNotification = System.currentTimeMillis() + NOTIFICATION_COOLDOWN_PERIOD
        val notificationMeta = mapOf("destination" to path)
        val job: Job

        try {
            job = BackgroundScope.launch {
                NotificationDescriptions.create.call(
                    CreateNotification(
                        ctx.user,
                        Notification(
                            NOTIFICATION_EXTRACTION_TYPE,
                            "Extraction started: '$archiveName'",
                            meta = notificationMeta
                        )
                    ),
                    serviceCloud
                )
            }

            sequence.forEach { entry ->
                log.debug("New Entry: $entry}")
                try {
                    if (System.currentTimeMillis() > nextNotification) {
                        nextNotification = System.currentTimeMillis() + NOTIFICATION_COOLDOWN_PERIOD
                        BackgroundScope.launch {
                            job.join()

                            NotificationDescriptions.create.call(
                                CreateNotification(
                                    ctx.user,
                                    Notification(
                                        NOTIFICATION_EXTRACTION_TYPE,
                                        "Extraction in progress: '$archiveName'",
                                        meta = notificationMeta
                                    )
                                ),
                                serviceCloud
                            )
                        }
                    }

                    if (rejectedDirectories.any { entry.path.startsWith(it) }) {
                        log.debug("Skipping entry: $entry")
                        rejectedFiles += entry.path
                        return@forEach
                    }

                    if (entry is ArchiveEntry.Directory && entry.path in createdDirectories) {
                        return@forEach
                    }

                    val existing = fs.statOrNull(ctx, entry.path, setOf(FileAttribute.FILE_TYPE))

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
                                        sensitivityService.setSensitivityLevel(ctx, targetPath, sensitivity)
                                    }
                                }

                                is ArchiveEntry.File -> {
                                    val newFile =
                                        fs.write(ctx, targetPath, conflictPolicy) { entry.stream.copyTo(this) }
                                    if (sensitivity != null) sensitivityService.setSensitivityLevel(
                                        ctx,
                                        newFile,
                                        sensitivity
                                    )
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

        BackgroundScope.launch {
            job.join()

            NotificationDescriptions.create.call(
                CreateNotification(
                    ctx.user,
                    Notification(
                        NOTIFICATION_EXTRACTION_TYPE,
                        "Extraction finished: '$archiveName'",
                        meta = notificationMeta
                    )
                ),
                serviceCloud
            )
        }

        return rejectedFiles
    }
}

package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.storage.util.CappedInputStream
import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.joinPath
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

class BulkUploadService<Ctx : FSUserContext>(
    private val fs: CoreFileSystemService<Ctx>
) {
    fun bulkUpload(
        ctx: Ctx,
        path: String,
        format: String,
        policy: WriteConflictPolicy,
        stream: InputStream
    ): List<String> {
        return when (format) {
            "tgz" -> bulkUploadTarGz(ctx, path, policy, stream)
            else -> throw FSException.BadRequest("Unsupported format '$format'")
        }
    }

    private fun bulkUploadTarGz(
        ctx: Ctx,
        path: String,
        conflictPolicy: WriteConflictPolicy,
        stream: InputStream
    ): List<String> {
        val rejectedFiles = ArrayList<String>()
        val rejectedDirectories = ArrayList<String>()

        TarInputStream(GZIPInputStream(stream)).use {
            val createdDirectories = HashSet<String>()
            var entry: TarEntry? = it.nextEntry
            while (entry != null) {
                val initialTargetPath = joinPath(path, entry.name)
                val cappedStream = CappedInputStream(it, entry.size)
                if (entry.name.contains("PaxHeader/")) {
                    // This is some meta data stuff in the tarball. We don't want this
                    log.debug("Skipping entry: ${entry.name}")
                    cappedStream.skipRemaining()
                } else if (rejectedDirectories.any { entry?.name?.startsWith(it) == true }) {
                    log.debug("Skipping entry: ${entry.name}")
                    rejectedFiles += initialTargetPath
                    cappedStream.skipRemaining()
                } else {
                    log.debug("Downloading ${entry.name} isDir=${entry.isDirectory} (${entry.size} bytes)")

                    val existing = fs.statOrNull(ctx, initialTargetPath, setOf(FileAttribute.FILE_TYPE))

                    val targetPath: String? = if (existing != null) {
                        // TODO This is technically handled by upload also
                        val existingIsDirectory = existing.fileType == FileType.DIRECTORY
                        if (entry.isDirectory != existingIsDirectory) {
                            log.debug("Type of existing and new does not match. Rejecting regardless of policy")
                            rejectedDirectories += entry.name
                            null
                        } else {
                            if (entry.isDirectory) {
                                log.debug("Directory already exists. Skipping")
                                null
                            } else {
                                initialTargetPath // Renaming/rejection handled by upload
                            }
                        }
                    } else {
                        log.debug("File does not exist")
                        initialTargetPath
                    }

                    if (targetPath != null) {
                        log.debug("Accepting file $initialTargetPath ($targetPath)")

                        try {
                            if (entry.isDirectory) {
                                createdDirectories += targetPath
                                fs.makeDirectory(ctx, targetPath)
                            } else {
                                val parentDir = File(targetPath).parentFile.path
                                if (parentDir !in createdDirectories) {
                                    createdDirectories += parentDir
                                    try {
                                        fs.makeDirectory(ctx, parentDir)
                                    } catch (ex: FSException.AlreadyExists) {
                                        log.debug("Parent directory allready exists")
                                    }
                                }

                                fs.write(ctx, targetPath, conflictPolicy) { cappedStream.copyTo(this) }
                            }
                        } catch (ex: FSException.PermissionException) {
                            rejectedFiles += initialTargetPath
                        }
                    } else {
                        if (!entry.isDirectory) {
                            log.debug("Skipping file $initialTargetPath")
                            cappedStream.skipRemaining()
                            rejectedFiles += initialTargetPath
                        }
                    }
                }

                entry = it.nextEntry
            }
        }
        return rejectedFiles
    }

    companion object : Loggable {
        override val log = logger()
    }
}

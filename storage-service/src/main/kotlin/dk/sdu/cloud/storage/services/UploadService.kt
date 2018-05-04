package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.BulkUploadOverwritePolicy
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.util.CappedInputStream
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

class UploadService(
    private val fs: FileSystemService,
    private val checksumService: ChecksumService
) {
    fun upload(user: String, path: String, writer: OutputStream.() -> Unit) {
        if (path.contains("\n")) throw FileSystemException.BadRequest("Bad filename")

        fs.write(user, path, writer)
        checksumService.computeAndAttachChecksum(user, path)
    }

    fun bulkUpload(
        user: String,
        path: String,
        format: String,
        policy: BulkUploadOverwritePolicy,
        stream: InputStream
    ): List<String> {
        return when (format) {
            "tgz" -> bulkUploadTarGz(user, path, policy, stream)
            else -> throw FileSystemException.BadRequest("Unsupported format '$format'")
        }
    }

    private fun bulkUploadTarGz(
        user: String,
        path: String,
        policy: BulkUploadOverwritePolicy,
        stream: InputStream
    ): List<String> {
        val rejectedFiles = ArrayList<String>()
        val rejectedDirectories = ArrayList<String>()

        TarInputStream(GZIPInputStream(stream)).use {
            var entry: TarEntry? = it.nextEntry
            while (entry != null) {
                val initialTargetPath = fs.joinPath(path, entry.name)
                val cappedStream = CappedInputStream(it, entry.size)
                if (entry.name.contains("PaxHeader/")) {
                    log.debug("Skipping entry: ${entry.name}")
                    cappedStream.skipRemaining()
                } else if (rejectedDirectories.any { entry?.name?.startsWith(it) == true }) {
                    log.debug("Skipping entry: ${entry.name}")
                    rejectedFiles += initialTargetPath
                    cappedStream.skipRemaining()
                } else {
                    println("Downloading ${entry.name} isDir=${entry.isDirectory} (${entry.size} bytes)")

                    val existing = fs.stat(user, initialTargetPath)

                    val targetPath: String? = if (existing != null) {
                        val existingIsDirectory = existing.type == FileType.DIRECTORY
                        if (entry.isDirectory != existingIsDirectory) {
                            log.debug("Type of existing and new does not match. Rejecting regardless of policy")
                            rejectedDirectories += entry.name
                            null
                        } else {
                            if (entry.isDirectory) {
                                log.debug("Directory already exists. Skipping")
                                null
                            } else {
                                when (policy) {
                                    BulkUploadOverwritePolicy.OVERWRITE -> {
                                        log.debug("Overwriting file")
                                        initialTargetPath
                                    }

                                    BulkUploadOverwritePolicy.RENAME -> {
                                        log.debug("Renaming file")
                                        fs.findFreeNameForNewFile(
                                            user,
                                            initialTargetPath
                                        )
                                    }

                                    BulkUploadOverwritePolicy.REJECT -> {
                                        log.debug("Rejecting file")
                                        null
                                    }
                                }
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
                                fs.mkdir(user, targetPath)
                            } else {
                                upload(user, targetPath) { cappedStream.copyTo(this) }
                            }
                        } catch (ex: FileSystemException.PermissionException) {
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

    companion object {
        private val log = LoggerFactory.getLogger(UploadService::class.java)
    }
}
package dk.sdu.cloud.app.abacus.services

import com.jcraft.jsch.SftpATTRS
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnection
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.createZipFileOfDirectory
import dk.sdu.cloud.app.abacus.services.ssh.lsWithGlob
import dk.sdu.cloud.app.abacus.services.ssh.mkdir
import dk.sdu.cloud.app.abacus.services.ssh.rm
import dk.sdu.cloud.app.abacus.services.ssh.scpDownload
import dk.sdu.cloud.app.abacus.services.ssh.scpUpload
import dk.sdu.cloud.app.abacus.services.ssh.stat
import dk.sdu.cloud.app.abacus.services.ssh.unzip
import dk.sdu.cloud.app.abacus.services.ssh.use
import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.FileForUploadArchiveType
import dk.sdu.cloud.app.api.SubmitComputationResult
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.jvm.javaio.copyTo
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files

/**
 * Manages file associated to a job
 */
class JobFileService(
    private val sshConnectionPool: SSHConnectionPool,
    private val cloud: AuthenticatedClient,
    workingDirectory: String
) {
    private val workingDirectory = File(workingDirectory)

    suspend fun initializeJob(jobId: String) {
        val status = sshConnectionPool.use {
            val rootDir = rootDirectoryForJob(jobId)
            val filesDir = filesDirectoryForJob(jobId)

            mkdir(rootDir.absolutePath, createParents = true)
            mkdir(filesDir.absolutePath, createParents = true)
        }

        if (status != 0) throw JobFileException.UnableToCreateFile()
    }

    suspend fun uploadFile(
        jobId: String,
        parameterName: String,
        length: Long?,
        stream: ByteReadChannel
    ) {
        val job = ComputationCallbackDescriptions.lookup.call(FindByStringId(jobId), cloud).orThrow()

        val file = job.files.find { it.id == parameterName } ?: throw RPCException(
            "Bad request. File with id '$parameterName' does not exist!",
            HttpStatusCode.BadRequest
        )

        val relativePath =
            if (file.destinationPath.startsWith("/")) ".${file.destinationPath}" else file.destinationPath

        val filesDirectoryForJob = filesDirectoryForJob(jobId)
        val location = filesDirectoryForJob.resolve(relativePath).normalize()
        if (!location.absolutePath.startsWith(filesDirectoryForJob.absolutePath)) {
            throw JobFileException.ErrorDuringTransfer("Bad destination path: $filesDirectoryForJob <=> $location")
        }

        sshConnectionPool.use {
            mkdir(location.parent, createParents = true)

            @Suppress("TooGenericExceptionCaught")
            try {
                if (length != null) {
                    scpUpload(length, location.name, location.parent, "0600") { outs ->
                        stream.copyTo(outs, limit = length)
                    }.takeIf { it == 0 } ?: throw JobFileException.ErrorDuringTransfer(relativePath)
                } else {
                    val temporaryFile = Files.createTempFile("", "").toFile()
                    temporaryFile.outputStream().use { outs ->
                        stream.copyTo(outs)
                    }

                    scpUpload(temporaryFile.length(), location.name, location.parent, "0600") { outs ->
                        temporaryFile.inputStream().use { ins ->
                            ins.copyTo(outs)
                        }
                    }.takeIf { it == 0 } ?: throw JobFileException.ErrorDuringTransfer(relativePath)
                }
            } catch (ex: Exception) {
                // Maybe a bit too generic
                log.warn("Caught exception during upload")
                log.warn(ex.stackTraceToString())
                throw JobFileException.ErrorDuringTransfer(relativePath)
            }

            when (file.needsExtractionOfType) {
                FileForUploadArchiveType.ZIP -> {
                    val status = unzip(location.absolutePath, location.parent)
                    if (status >= ZIP_ERROR_STATUS) {
                        throw JobFileException.CouldNotExtractArchive(relativePath)
                    }

                    rm(location.absolutePath)
                }

                else -> {
                    // Do nothing
                }
            }
        }
    }

    suspend fun cleanup(jobId: String) {
        sshConnectionPool.use {
            rm(rootDirectoryForJob(jobId).absolutePath, recurse = true)
        }
    }

    suspend fun transferForJob(job: VerifiedJob) {
        val directory = filesDirectoryForJob(job.id)

        sshConnectionPool.use {
            val outputs = job.application.invocation.outputFileGlobs
                .flatMap { lsWithGlob(directory.absolutePath, it) }
                .map { File(it.fileName).absolutePath }

            outputs.mapNotNull { transfer ->
                val source = directory.resolve(transfer)
                if (!source.path.startsWith(directory.path)) {
                    log.warn("File $transfer did not resolve to be within working directory. $source versus $directory")
                    return@mapNotNull null
                }

                val sourceFile = stat(source.path).also { sourceFile ->
                    log.debug("Transferring file ${source.path}")
                    log.debug("Got back: $sourceFile")
                }

                if (sourceFile == null) {
                    log.info("Could not find output file at: ${source.path}. Skipping file")
                    return@mapNotNull null
                }

                val (fileToTransferFromHPC, _, needsExtraction) = prepareForTransfer(sourceFile, source, transfer)
                log.debug("Downloading file from $fileToTransferFromHPC")

                GlobalScope.launch {
                    transferFileFromCompute(job.id, fileToTransferFromHPC, transfer, needsExtraction)
                }
            }.joinAll()
        }

    }

    private suspend fun SSHConnection.transferFileFromCompute(
        jobId: String,
        filePath: String,
        originalFileName: String,
        needsExtraction: Boolean
    ) {
        val temporaryFile = Files.createTempFile("", "'").toFile()
        @Suppress("TooGenericExceptionCaught")
        try {
            val parsedFilePath = File(filePath)
            val filesRoot = filesDirectoryForJob(jobId)
            val relativePath = parsedFilePath.relativeTo(filesRoot).path

            val status = scpDownload(filePath) { ins ->
                temporaryFile.outputStream().use { outs ->
                    ins.copyTo(outs)
                }
            }

            if (status != 0) throw JobFileException.UploadToCloudFailed(originalFileName)

            ComputationCallbackDescriptions.submitFile
                .call(
                    SubmitComputationResult(
                        jobId,
                        relativePath,
                        needsExtraction,
                        BinaryStream.outgoingFromChannel(
                            temporaryFile.readChannel(),
                            contentLength = temporaryFile.length()
                        )
                    ),
                    cloud
                )
                .orRethrowAs { throw JobFileException.UploadToCloudFailed(originalFileName) }
        } catch (ex: Exception) {
            log.warn("Caught exception while uploading file to SDUCloud")
            log.warn(ex.stackTraceToString())
            throw JobFileException.UploadToCloudFailed(originalFileName)
        } finally {
            temporaryFile.delete()
        }
    }

    data class FileForTransfer(val path: String, val length: Long, val needsExtraction: Boolean)

    private suspend fun SSHConnection.prepareForTransfer(
        sourceFile: SftpATTRS,
        source: File,
        transfer: String
    ): FileForTransfer {
        return if (!sourceFile.isDir) {
            FileForTransfer(source.path, sourceFile.size, needsExtraction = false)
        } else {
            log.debug("Source file is a directory. Zipping it up")
            val zipPath = source.path + ".zip"
            val status = createZipFileOfDirectory(zipPath, source.path)

            if (status != 0) {
                log.warn("Unable to create zip archive of output!")
                log.warn("Path: ${source.path}")
                log.warn("Status: $status")
                throw JobFileException.ArchiveCreationFailed(transfer)
            }

            val zipStat = stat(zipPath) ?: run {
                log.warn("Unable to find zip file after creation. Expected it at: $zipPath")
                throw JobFileException.ArchiveCreationFailed(transfer)
            }

            FileForTransfer(zipPath, zipStat.size, needsExtraction = true)
        }
    }

    fun rootDirectoryForJob(jobId: String): File = workingDirectory.resolve(jobId)

    fun filesDirectoryForJob(jobId: String): File = rootDirectoryForJob(jobId).resolve(FILES_DIR)

    companion object : Loggable {
        private const val FILES_DIR = "files"
        private const val ZIP_ERROR_STATUS = 3

        override val log = logger()
    }
}

sealed class JobFileException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class UnableToCreateFile : JobFileException("Could not create file", HttpStatusCode.InternalServerError)
    class ErrorDuringTransfer(val file: String) :
        JobFileException("Error while transferring file: $file", HttpStatusCode.InternalServerError)

    class CouldNotExtractArchive(val file: String) :
        JobFileException("Could not extract archive: $file", HttpStatusCode.InternalServerError)

    class UploadToCloudFailed(val file: String) :
        JobFileException("Could not upload file to SDUCloud", HttpStatusCode.InternalServerError)

    class ArchiveCreationFailed(val file: String) :
        JobFileException("An error occured while creating an archive", HttpStatusCode.InternalServerError)

    class NotSupported(why: String) : JobFileException("Not supported: $why", HttpStatusCode.BadRequest)
}

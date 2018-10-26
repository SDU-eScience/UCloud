package dk.sdu.cloud.app.abacus.services

import com.jcraft.jsch.SftpATTRS
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
import dk.sdu.cloud.app.abacus.util.CappedInputStream
import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.FileForUploadArchiveType
import dk.sdu.cloud.app.api.SubmitComputationResult
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.io.InputStream

/**
 * Manages file associated to a job
 */
class JobFileService(
    private val sshConnectionPool: SSHConnectionPool,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    workingDirectory: String
) {
    private val workingDirectory = File(workingDirectory)

    fun initializeJob(jobId: String) {
        val status = sshConnectionPool.use {
            val rootDir = rootDirectoryForJob(jobId)
            val filesDir = filesDirectoryForJob(jobId)

            mkdir(rootDir.absolutePath, createParents = true)
            mkdir(filesDir.absolutePath, createParents = true)
        }

        if (status != 0) throw JobFileException.UnableToCreateFile()
    }

    fun uploadFile(
        jobId: String,
        relativeLocation: String,
        length: Long,
        needsExtractionOfType: FileForUploadArchiveType?,
        stream: InputStream
    ) {
        val location = filesDirectoryForJob(jobId).resolve(relativeLocation)
        val cappedStream = CappedInputStream(stream, length)
        sshConnectionPool.use {
            mkdir(location.parent, createParents = true)

            @Suppress("TooGenericExceptionCaught")
            try {
                scpUpload(length, location.name, location.parent, "0600") { outs ->
                    cappedStream.copyTo(outs)
                }.takeIf { it == 0 } ?: throw JobFileException.ErrorDuringTransfer(relativeLocation)
            } catch (ex: Exception) {
                // Maybe a bit too generic
                log.warn("Caught exception during upload")
                log.warn(ex.stackTraceToString())
                throw JobFileException.ErrorDuringTransfer(relativeLocation)
            }

            when (needsExtractionOfType) {
                FileForUploadArchiveType.ZIP -> {
                    val status = unzip(location.absolutePath, location.parent)
                    if (status >= ZIP_ERROR_STATUS) {
                        throw JobFileException.CouldNotExtractArchive(relativeLocation)
                    }
                }

                else -> {
                    // Do nothing
                }
            }
        }
    }

    fun cleanup(jobId: String) {
        sshConnectionPool.use {
            rm(rootDirectoryForJob(jobId).absolutePath, recurse = true)
        }
    }

    fun transferForJob(job: VerifiedJob) {
        val directory = filesDirectoryForJob(job.id)

        sshConnectionPool.use {
            val outputs = job.application.description.outputFileGlobs
                .flatMap { lsWithGlob(directory.absolutePath, it) }
                .map { File(it.fileName).absolutePath }

            for (transfer in outputs) {
                val source = directory.resolve(transfer)
                if (!source.path.startsWith(directory.path)) {
                    log.warn("File $transfer did not resolve to be within working directory. $source versus $directory")
                    continue
                }

                val sourceFile = stat(source.path).also { sourceFile ->
                    log.debug("Transferring file ${source.path}")
                    log.debug("Got back: $sourceFile")
                }

                if (sourceFile == null) {
                    log.info("Could not find output file at: ${source.path}. Skipping file")
                    continue
                }

                val (fileToTransferFromHPC, fileLength) = prepareForTransfer(sourceFile, source, transfer)
                log.debug("Downloading file from $fileToTransferFromHPC")

                transferFileFromCompute(job.id, fileToTransferFromHPC, transfer, fileLength)
            }
        }

    }

    private fun SSHConnection.transferFileFromCompute(
        jobId: String,
        filePath: String,
        originalFileName: String,
        length: Long
    ) {
        val transferStatus =
            @Suppress("TooGenericExceptionCaught")
            try {
                val parsedFilePath = File(filePath)
                val filesRoot = filesDirectoryForJob(jobId)
                val relativePath = parsedFilePath.relativeTo(filesRoot).path

                scpDownload(filePath) { ins ->
                    runBlocking {
                        ComputationCallbackDescriptions.submitFile.call(
                            MultipartRequest.create(
                                SubmitComputationResult(
                                    jobId,
                                    relativePath,
                                    StreamingFile(
                                        ContentType.Application.OctetStream,
                                        length,
                                        relativePath,
                                        ins
                                    )
                                )
                            ),
                            cloud
                        )
                    } as? RESTResponse.Ok ?: throw JobFileException.UploadToCloudFailed(originalFileName)
                }
            } catch (ex: Exception) {
                log.warn("Caught exception while uploading file to SDUCloud")
                log.warn(ex.stackTraceToString())
                throw JobFileException.UploadToCloudFailed(originalFileName)
            }

        if (transferStatus != 0) {
            throw JobFileException.UploadToCloudFailed(originalFileName)
        }
    }

    private fun SSHConnection.prepareForTransfer(
        sourceFile: SftpATTRS,
        source: File,
        transfer: String
    ): Pair<String, Long> {
        return if (!sourceFile.isDir) {
            Pair(source.path, sourceFile.size)
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

            Pair(zipPath, zipStat.size)
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

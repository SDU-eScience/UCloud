package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.ExtractRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SimpleUploadRequest
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.service.Loggable
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.http.isSuccess
import kotlinx.coroutines.io.ByteReadChannel
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class JobFileService(
    private val client: AuthenticatedClient
) {
    private fun AuthenticatedClient.bearerAuth(token: String): AuthenticatedClient {
        return ClientAndBackend(client, companion).bearerAuth(token)
    }

    suspend fun initializeResultFolder(
        jobWithToken: VerifiedJobWithAccessToken,
        isReplay: Boolean = false
    ) {
        val (job, accessToken) = jobWithToken

        val userCloud = client.bearerAuth(accessToken)

        val path = jobFolder(job)
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path.parent(), null),
            userCloud
        )

        val dirResp = FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(path, null),
            userCloud
        ).throwIfInternal()

        if (!dirResp.statusCode.isSuccess()) {
            // We allow conflicts during replay
            if (isReplay && dirResp.statusCode == HttpStatusCode.Conflict) return

            // Throw if we didn't allow this case
            throw RPCException.fromStatusCode(dirResp.statusCode)
        }
    }

    suspend fun acceptFile(
        jobWithToken: VerifiedJobWithAccessToken,

        filePath: String,
        length: Long,
        fileData: ByteReadChannel,
        needsExtraction: Boolean
    ) {
        log.debug("Accepting file at $filePath for ${jobWithToken.job.id}")

        val parsedFilePath = File(filePath)
        val relativePath = if (parsedFilePath.isAbsolute) {
            ".$filePath" // TODO This might be a bad idea
        } else {
            filePath
        }

        log.debug("The relative path is $relativePath")

        val rootDestination = File(jobFolder(jobWithToken.job))
        val destPath = File(rootDestination, relativePath)

        log.debug("The destination path is ${destPath.path}")

        val sensitivityLevel =
            jobWithToken.job.files.map { it.stat.sensitivityLevel }.sortedByDescending { it.ordinal }.max()
                ?: SensitivityLevel.PRIVATE

        val (_, accessToken) = jobWithToken
        MultiPartUploadDescriptions.simpleUpload.call(
            SimpleUploadRequest(
                location = destPath.path,
                sensitivity = sensitivityLevel,
                file = BinaryStream.outgoingFromChannel(
                    fileData,
                    contentLength = length,
                    contentType = ContentType.defaultForFilePath(filePath)
                )
            ),
            client.bearerAuth(accessToken)
        ).orThrow()

        if (needsExtraction) {
            FileDescriptions.extract.call(
                ExtractRequest(
                    destPath.path,
                    removeOriginalArchive = true
                ),
                client.bearerAuth(accessToken)
            ).orThrow()
        }
    }

    suspend fun jobFolder(job: VerifiedJob): String {
        val homeFolder = FileDescriptions.findHomeFolder.call(
            FindHomeFolderRequest(job.owner),
            client
        ).orThrow().path

        return joinPath(
            homeFolder,
            "Jobs",
            job.archiveInCollection,
            timestampFormatter.format(LocalDateTime.ofInstant(Date(job.createdAt).toInstant(), zoneId))
        )
    }


    companion object : Loggable {
        override val log = logger()

        private val zoneId = ZoneId.of("Europe/Copenhagen")
        private val timestampFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS")
    }
}

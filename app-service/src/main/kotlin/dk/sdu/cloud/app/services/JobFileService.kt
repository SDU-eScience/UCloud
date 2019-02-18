package dk.sdu.cloud.app.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.calls.types.StreamingRequest
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.UploadRequest
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.service.Loggable
import io.ktor.http.ContentType
import io.ktor.http.defaultForFilePath
import kotlinx.coroutines.io.ByteReadChannel
import java.io.File

class JobFileService(
    private val client: AuthenticatedClient
) {
    private fun AuthenticatedClient.bearerAuth(token: String): AuthenticatedClient {
        return ClientAndBackend(client, companion).bearerAuth(token)
    }

    suspend fun initializeResultFolder(
        jobWithToken: VerifiedJobWithAccessToken
    ) {
        val (job, accessToken) = jobWithToken

        val userCloud = client.bearerAuth(accessToken)
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(jobFolder(job.id, job.owner), null),
            userCloud
        ).orThrow()
    }

    suspend fun acceptFile(
        jobWithToken: VerifiedJobWithAccessToken,

        filePath: String,
        length: Long,
        fileData: ByteReadChannel
    ) {
        log.debug("Accepting file at $filePath for ${jobWithToken.job.id}")

        val parsedFilePath = File(filePath)
        val relativePath = if (parsedFilePath.isAbsolute) {
            ".$filePath" // TODO This might be a bad idea
        } else {
            filePath
        }

        log.debug("The relative path is $relativePath")

        val rootDestination = File(jobFolder(jobWithToken.job.id, jobWithToken.job.owner))
        val destPath = File(rootDestination, relativePath)

        log.debug("The destination path is ${destPath.path}")

        val sensitivityLevel =
            jobWithToken.job.files.map { it.stat.sensitivityLevel }.sortedByDescending { it.ordinal }.max()
                ?: SensitivityLevel.PRIVATE

        val (_, accessToken) = jobWithToken
        MultiPartUploadDescriptions.upload.call(
            StreamingRequest.Outgoing(
                UploadRequest(
                    location = destPath.path,
                    sensitivity = sensitivityLevel,
                    upload = StreamingFile(
                        contentType = ContentType.defaultForFilePath(filePath),
                        length = length,
                        fileName = destPath.name,
                        channel = fileData
                    )
                )
            ),
            client.bearerAuth(accessToken)
        )
    }

    private suspend fun jobFolder(jobId: String, user: String): String {
        val homeFolder = FileDescriptions.findHomeFolder.call(
            FindHomeFolderRequest(user),
            client
        ).orThrow().path
        return joinPath(homeFolder, "Jobs", jobId)
    }


    companion object : Loggable {
        override val log = logger()
    }
}

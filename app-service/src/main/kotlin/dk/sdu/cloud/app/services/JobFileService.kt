package dk.sdu.cloud.app.services

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.MultiPartUploadDescriptions
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.UploadRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.orThrow
import io.ktor.http.ContentType
import io.ktor.http.defaultForFilePath
import kotlinx.coroutines.io.ByteReadChannel
import java.io.File
import java.io.InputStream

class JobFileService(
    cloud: AuthenticatedCloud
) {
    private val cloudContext = cloud.parent

    suspend fun initializeResultFolder(
        jobWithToken: VerifiedJobWithAccessToken
    ) {
        val (job, accessToken) = jobWithToken

        val userCloud = cloudContext.jwtAuth(accessToken)
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

        val (_, accessToken) = jobWithToken
        MultiPartUploadDescriptions.upload.call(
            MultipartRequest.create(
                UploadRequest(
                    location = destPath.path,
                    sensitivity = SensitivityLevel.CONFIDENTIAL,
                    upload = StreamingFile(
                        contentType = ContentType.defaultForFilePath(filePath),
                        length = length,
                        fileName = destPath.name,
                        channel = fileData
                    )
                )
            ),
            cloudContext.jwtAuth(accessToken)
        )
    }

    private fun jobFolder(jobId: String, user: String): String = "/home/$user/Jobs/$jobId"

    companion object : Loggable {
        override val log = logger()
    }
}

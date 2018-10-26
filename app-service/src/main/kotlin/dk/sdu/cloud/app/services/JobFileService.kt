package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.copyFrom
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.upload.api.MultiPartUploadDescriptions
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
        fileData: InputStream
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
        MultiPartUploadDescriptions.callUpload(cloudContext, accessToken, destPath.path) {
            it.copyFrom(fileData)
        }
    }

    private fun jobFolder(jobId: String, user: String): String = "/home/$user/Jobs/$jobId"

    companion object : Loggable {
        override val log = logger()
    }
}

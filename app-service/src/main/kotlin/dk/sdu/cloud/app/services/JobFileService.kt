package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.copyFrom
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.upload.api.MultiPartUploadDescriptions
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
        val (_, accessToken) = jobWithToken
        MultiPartUploadDescriptions.callUpload(cloudContext, accessToken, filePath) { it.copyFrom(fileData) }
    }

    private fun jobFolder(jobId: String, user: String): String = "/home/$user/Jobs/$jobId"
}

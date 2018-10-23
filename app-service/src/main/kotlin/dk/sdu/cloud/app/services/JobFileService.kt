package dk.sdu.cloud.app.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.api.copyFrom
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.upload.api.MultiPartUploadDescriptions
import java.io.InputStream

class JobFileService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val jobDao: JobDao<DBSession>,
    private val cloud: AuthenticatedCloud
) {
    private val cloudContext = cloud.parent

    suspend fun initializeResultFolder(
        securityPrincipal: SecurityPrincipal,
        jobId: String
    ) {
        val (job, accessToken) = db.withTransaction { session -> jobDao.findOrNull(session, jobId) }
                ?: throw JobException.NotFound("job: $jobId")

        val userCloud = cloudContext.jwtAuth(accessToken)
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(jobFolder(job.id, job.owner), null),
            userCloud
        ).orThrow()
    }

    suspend fun acceptFile(
        securityPrincipal: SecurityPrincipal,
        jobId: String,

        filePath: String,
        fileData: InputStream
    ) {
        val (_, accessToken) = db.withTransaction { session -> jobDao.findOrNull(session, jobId) }
                ?: throw JobException.NotFound("job: $jobId")

        // TODO The job directory should be created when we move into TRANSFER_SUCCESS
        MultiPartUploadDescriptions.callUpload(cloudContext, accessToken, filePath) { it.copyFrom(fileData) }
    }

    private fun jobFolder(jobId: String, user: String): String = "/home/$user/Jobs/$jobId"
}
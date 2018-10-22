package dk.sdu.cloud.app.services

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.FileForUploadArchiveType
import dk.sdu.cloud.app.api.FileTransferDescription
import dk.sdu.cloud.app.api.ValidatedFileForUpload
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.experimental.async
import java.io.File
import java.net.URI
import java.util.*

data class UnverifiedJob(
    val request: AppRequest.Start,
    val principal: DecodedJWT
)

class JobVerificationService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val applicationDAO: ApplicationDAO<DBSession>
) {
    suspend fun verifyOrThrow(
        unverifiedJob: UnverifiedJob,
        cloud: AuthenticatedCloud
    ): VerifiedJob {
        val jobId = UUID.randomUUID().toString()
        val application = findApplication(unverifiedJob)
        val verifiedParameters = verifyParameters(application, unverifiedJob)
        val workDir = URI("/$jobId")
        val files = collectFiles(application, verifiedParameters, workDir, cloud)

        val numberOfJobs = unverifiedJob.request.numberOfNodes ?: application.tool.description.defaultNumberOfNodes
        val tasksPerNode = unverifiedJob.request.tasksPerNode ?: application.tool.description.defaultTasksPerNode
        val maxTime = unverifiedJob.request.maxTime ?: application.tool.description.defaultMaxTime

        return VerifiedJob(
            application,
            files,
            jobId,
            unverifiedJob.principal.subject,
            numberOfJobs,
            tasksPerNode,
            maxTime,
            unverifiedJob.principal.token,
            verifiedParameters
        )
    }

    private fun findApplication(job: UnverifiedJob): Application = db.withTransaction { session ->
        try {
            with(job.request.application) {
                applicationDAO.findByNameAndVersion(session, job.principal.subject, name, version)
            }
        } catch (ex: ApplicationException.NotFound) {
            throw JobValidationException("Application '${job.request.application}' does not exist")
        }
    }

    private fun verifyParameters(app: Application, job: UnverifiedJob): VerifiedJobInput {
        val userParameters = job.request.parameters
        return VerifiedJobInput(
            app.description.parameters.map { appParameter ->
                try {
                    appParameter.name to appParameter.map(userParameters[appParameter.name])
                } catch (ex: IllegalArgumentException) {
                    throw JobValidationException("Bad parameter: ${appParameter.name}. ${ex.message}")
                }
            }.toMap()
        )
    }

    private suspend fun collectFiles(
        application: Application,
        verifiedParameters: VerifiedJobInput,
        workDir: URI,
        cloud: AuthenticatedCloud
    ): List<ValidatedFileForUpload> {
        return application.description.parameters
            .asSequence()
            .filter { it is ApplicationParameter.InputFile || it is ApplicationParameter.InputDirectory }
            .map {
                @Suppress("UNCHECKED_CAST")
                val appParameter = it as ApplicationParameter<FileTransferDescription>

                async { collectSingleFile(verifiedParameters, workDir, cloud, appParameter) }
            }
            .toList()
            .mapNotNull { it.await() }
    }

    private suspend fun collectSingleFile(
        verifiedParameters: VerifiedJobInput,
        workDir: URI,
        cloud: AuthenticatedCloud,
        fileAppParameter: ApplicationParameter<FileTransferDescription>
    ): ValidatedFileForUpload? {
        val desiredFileType = when (fileAppParameter) {
            is ApplicationParameter.InputDirectory -> FileType.DIRECTORY
            else -> FileType.FILE
        }

        val transferDescription = verifiedParameters[fileAppParameter] ?: return null

        val sourcePath = transferDescription.source
        val stat = FileDescriptions.stat.call(FindByPath(sourcePath), cloud)
            .orThrowOnError {
                throw JobValidationException("Missing file in storage: $sourcePath. Are you sure it exists?")
            }
            .result

        if (stat.fileType != desiredFileType) {
            throw JobValidationException(
                "Expected type of ${fileAppParameter.name} to be " +
                        "$desiredFileType, but instead got a ${stat.fileType}"
            )
        }

        // Resolve relative path against working directory. Ensure that file is still inside of
        // the working directory.
        val destinationPath = File(workDir.toURL().path, transferDescription.destination).normalize().path
        if (!destinationPath.startsWith(workDir.path)) {
            throw JobValidationException(
                "Not allowed to leave working directory via relative paths. Please avoid using '..' in paths."
            )
        }

        val name = destinationPath.split("/").last()

        return ValidatedFileForUpload(
            fileAppParameter.name,
            stat,
            name,
            destinationPath,
            sourcePath,
            if (desiredFileType == FileType.DIRECTORY) FileForUploadArchiveType.ZIP else null
        )
    }
}

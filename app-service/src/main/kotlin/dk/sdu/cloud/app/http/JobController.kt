package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.JobDescriptions
import dk.sdu.cloud.app.api.JobStartedResponse
import dk.sdu.cloud.app.services.JobExecutionService
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.bearer
import dk.sdu.cloud.service.cloudClient
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.jobId
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.withCausedBy
import dk.sdu.cloud.upload.api.MultiPartUploadDescriptions
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

private const val ONE_DAY_IN_MILLS = 1000 * 60 * 60 * 24L

class JobController<DBSession>(
    private val jobExecutionService: JobExecutionService<DBSession>
) : Controller {
    override val baseContext = JobDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(JobDescriptions.findById) {
            logEntry(log, it)
            error(CommonErrorMessage("Not yet implemented"), HttpStatusCode.BadRequest)
        }

        implement(JobDescriptions.listRecent) {
            logEntry(log, it)
            error(CommonErrorMessage("Not yet implemented"), HttpStatusCode.BadRequest)
        }

        implement(JobDescriptions.start) { req ->
            logEntry(log, req)
            val extensionResponse = AuthDescriptions.tokenExtension.call(
                TokenExtensionRequest(
                    call.request.bearer!!,
                    listOf(
                        MultiPartUploadDescriptions.upload.requiredAuthScope.toString(),
                        FileDescriptions.download.requiredAuthScope.toString(),
                        FileDescriptions.createDirectory.requiredAuthScope.toString()
                    ),
                    ONE_DAY_IN_MILLS
                ),
                call.cloudClient
            )

            if (extensionResponse !is RESTResponse.Ok) {
                error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                return@implement
            }

            val extendedToken = TokenValidation.validate(extensionResponse.result.accessToken)

            val userCloud = JWTAuthenticatedCloud(
                call.cloudClient.parent,
                extendedToken.token
            ).withCausedBy(call.request.jobId)

            val jobId = jobExecutionService.startJob(req, extendedToken, userCloud)
            ok(JobStartedResponse(jobId))
        }

        implement(JobDescriptions.follow) { req ->
            logEntry(log, req)
            error(CommonErrorMessage("Not yet implemented"), HttpStatusCode.BadRequest)
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(JobController::class.java)
    }
}

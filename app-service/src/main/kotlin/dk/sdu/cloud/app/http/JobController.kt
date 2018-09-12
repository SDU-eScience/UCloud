package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.HPCJobDescriptions
import dk.sdu.cloud.app.api.JobStartedResponse
import dk.sdu.cloud.app.services.JobException
import dk.sdu.cloud.app.services.JobService
import dk.sdu.cloud.app.services.JobServiceException
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.*
import dk.sdu.cloud.upload.api.MultiPartUploadDescriptions
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory

class JobController<DBSession>(
    private val jobService: JobService<DBSession>
) : Controller {
    override val baseContext = HPCJobDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(HPCJobDescriptions.findById) {
            logEntry(log, it)
            val user = call.securityPrincipal.username
            val result = jobService.findJobById(user, it.id)
            if (result == null) {
                error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
            } else {
                ok(result)
            }
        }

        implement(HPCJobDescriptions.listRecent) {
            logEntry(log, it)
            val user = call.securityPrincipal.username
            ok(jobService.recentJobs(user, it))
        }

        implement(HPCJobDescriptions.start) { req ->
            logEntry(log, req)
            try {
                val extensionResponse = AuthDescriptions.tokenExtension.call(
                    TokenExtensionRequest(
                        call.request.bearer!!,
                        listOf(
                            MultiPartUploadDescriptions.upload.requiredAuthScope.toString(),
                            FileDescriptions.download.requiredAuthScope.toString(),
                            FileDescriptions.createDirectory.requiredAuthScope.toString()
                        ),
                        1000 * 60 * 60 * 24L
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

                val uuid = jobService.startJob(extendedToken, req, userCloud)
                ok(JobStartedResponse(uuid))
            } catch (ex: JobException) {
                if (ex.statusCode.value in 500..599) log.warn(ex.stackTraceToString())
                error(CommonErrorMessage(ex.message ?: "An error has occurred"), ex.statusCode)
            }
        }

        implement(HPCJobDescriptions.follow) { req ->
            logEntry(log, req)

            val user = call.securityPrincipal.username
            val job = jobService.findJobForInternalUseById(user, req.jobId) ?: return@implement run {
                log.debug("Could not find job id: ${req.jobId}")
                error(CommonErrorMessage("Job not found"), HttpStatusCode.NotFound)
            }

            val result = try {
                jobService.followStdStreams(req, job)
            } catch (ex: JobServiceException) {
                error(CommonErrorMessage(ex.message ?: "Unknown error"), ex.statusCode)
                return@implement
            }

            ok(result)
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(JobController::class.java)
    }
}

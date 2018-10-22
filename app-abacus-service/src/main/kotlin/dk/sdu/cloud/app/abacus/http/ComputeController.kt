package dk.sdu.cloud.app.abacus.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.abacus.api.AbacusComputationDescriptions
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.app.api.ComputationDescriptions
import dk.sdu.cloud.app.api.ComputationErrorMessage
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.streamProvider
import io.ktor.request.receiveMultipart
import io.ktor.routing.Route
import java.io.InputStream

class ComputeController(
    private val jobFileService: JobFileService,
    private val slurmService: SlurmScheduler
) : Controller {
    override val baseContext: String = AbacusComputationDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(AbacusComputationDescriptions.jobVerified) { req ->
            logEntry(log, req)

            jobFileService.initializeJob(req.id)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.submitFile) { rawReq ->
            logEntry(log, rawReq)

            parseSubmitFileOrFail { req ->
                val file = req.job.files.find { it.id == req.parameterName } ?: throw RPCException(
                    "Bad request. File with id '${req.parameterName}' does not exist!",
                    HttpStatusCode.BadRequest
                )

                jobFileService.uploadFile(
                    req.job.id,
                    file.destinationPath,
                    file.stat.size,
                    file.needsExtractionOfType,
                    req.fileProvider
                )
            }

            ok(Unit)
        }

        implement(AbacusComputationDescriptions.jobPrepared) { req ->
            logEntry(log, req)

            slurmService.schedule(req)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.cleanup) { req ->
            logEntry(log, req)

            jobFileService.cleanup(req.id)
            ok(Unit)
        }
    }

    private data class SubmitFileRequest(
        val job: VerifiedJob,
        val parameterName: String,
        val fileProvider: InputStream
    )

    private suspend inline fun RESTHandler<*, *, ComputationErrorMessage, *>.parseSubmitFileOrFail(
        handler: (SubmitFileRequest) -> Unit
    ) {
        var job: VerifiedJob? = null
        var jobParamName: String? = null

        val multipart = call.receiveMultipart()
        while (true) {
            val part = multipart.readPart() ?: break

            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        ComputationDescriptions.SUBMIT_FILE_FIELD_JOB -> {
                            try {
                                job = defaultMapper.readValue(part.value)
                            } catch (ex: Exception) {
                                log.info("Could not parse job")
                                log.info(ex.stackTraceToString())
                                throw RPCException("Bad request (invalid job)", HttpStatusCode.BadRequest)
                            }
                        }

                        ComputationDescriptions.SUBMIT_FILE_FIELD_PARAM_NAME -> jobParamName = part.value
                    }
                }

                is PartData.FileItem -> {
                    val capturedJob = job
                    val capturedJobParamName = jobParamName
                    if (capturedJob == null || capturedJobParamName == null) {
                        throw RPCException("Bad request", HttpStatusCode.BadRequest)
                    }

                    val stream = part.streamProvider()
                    val request = SubmitFileRequest(capturedJob, capturedJobParamName, stream)
                    handler(request)
                }
            }

            part.dispose()
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

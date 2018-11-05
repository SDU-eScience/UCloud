package dk.sdu.cloud.app.abacus.http

import dk.sdu.cloud.app.abacus.api.AbacusComputationDescriptions
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.JobTail
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.implement
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class ComputeController(
    private val jobFileService: JobFileService,
    private val slurmService: SlurmScheduler<*>,
    private val jobTail: JobTail
) : Controller {
    override val baseContext: String = AbacusComputationDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(AbacusComputationDescriptions.jobVerified) { req ->
            jobFileService.initializeJob(req.id)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.submitFile) { multipart ->
            multipart.receiveBlocks { block ->
                val file = block.job.files.find { it.id == block.parameterName } ?: throw RPCException(
                    "Bad request. File with id '${block.parameterName}' does not exist!",
                    HttpStatusCode.BadRequest
                )
                val relativePath =
                    if (file.destinationPath.startsWith("/")) ".${file.destinationPath}" else file.destinationPath

                jobFileService.uploadFile(
                    block.job.id,
                    relativePath,
                    file.stat.size,
                    file.needsExtractionOfType,
                    block.fileData.payload
                )
            }

            ok(Unit)
        }

        implement(AbacusComputationDescriptions.jobPrepared) { req ->
            slurmService.schedule(req)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.cleanup) { req ->
            jobFileService.cleanup(req.id)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.follow) { req ->
            ok(jobTail.followStdStreams(req))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

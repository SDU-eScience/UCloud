package dk.sdu.cloud.app.abacus.http

import dk.sdu.cloud.app.abacus.api.AbacusComputationDescriptions
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.JobTail
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class ComputeController(
    private val jobFileService: JobFileService,
    private val slurmService: SlurmScheduler<*>,
    private val jobTail: JobTail
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AbacusComputationDescriptions.jobVerified) {
            jobFileService.initializeJob(request.id)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.submitFile) {
            request.asIngoing().receiveBlocks { block ->
                val file = block.job.files.find { it.id == block.parameterName } ?: throw RPCException(
                    "Bad request. File with id '${block.parameterName}' does not exist!",
                    HttpStatusCode.BadRequest
                )
                val relativePath =
                    if (file.destinationPath.startsWith("/")) ".${file.destinationPath}" else file.destinationPath


                jobFileService.uploadFile(
                    block.job.id,
                    relativePath,
                    block.fileData.length,
                    file.needsExtractionOfType,
                    block.fileData.channel
                )
            }

            ok(Unit)
        }

        implement(AbacusComputationDescriptions.jobPrepared) {
            slurmService.schedule(request)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.cleanup) {
            jobFileService.cleanup(request.id)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.follow) {
            ok(jobTail.followStdStreams(request))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

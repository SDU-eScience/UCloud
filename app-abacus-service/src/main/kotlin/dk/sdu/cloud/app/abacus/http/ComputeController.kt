package dk.sdu.cloud.app.abacus.http

import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.JobTail
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.app.api.ComputationDescriptions
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

class ComputeController(
    private val jobFileService: JobFileService,
    private val slurmService: SlurmScheduler<*>,
    private val jobTail: JobTail,
    private val rpcInterface: ComputationDescriptions
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(rpcInterface.jobVerified) {
            jobFileService.initializeJob(request.id)
            ok(Unit)
        }

        implement(rpcInterface.submitFile) {
            val asIngoing = request.fileData.asIngoing()
            jobFileService.uploadFile(
                request.jobId,
                request.parameterName,
                asIngoing.length,
                asIngoing.channel
            )

            ok(Unit)
        }

        implement(rpcInterface.jobPrepared) {
            slurmService.schedule(request)
            ok(Unit)
        }

        implement(rpcInterface.cleanup) {
            jobFileService.cleanup(request.id)
            ok(Unit)
        }

        implement(rpcInterface.follow) {
            ok(jobTail.followStdStreams(request))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

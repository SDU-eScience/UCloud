package dk.sdu.cloud.app.abacus.http

import dk.sdu.cloud.app.abacus.api.AbacusComputationDescriptions
import dk.sdu.cloud.app.abacus.services.JobFileService
import dk.sdu.cloud.app.abacus.services.SlurmScheduler
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class ComputeController(
    private val jobFileService: JobFileService,
    private val slurmService: SlurmScheduler<*>
) : Controller {
    override val baseContext: String = AbacusComputationDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(AbacusComputationDescriptions.jobVerified) { req ->
            logEntry(log, req)

            jobFileService.initializeJob(req.id)
            ok(Unit)
        }

        implement(AbacusComputationDescriptions.submitFile) { multipart ->
            logEntry(log, multipart)

            multipart.receiveBlocks { block ->
                val file = block.job.files.find { it.id == block.parameterName } ?: throw RPCException(
                    "Bad request. File with id '${block.parameterName}' does not exist!",
                    HttpStatusCode.BadRequest
                )

                jobFileService.uploadFile(
                    block.job.id,
                    file.destinationPath,
                    file.stat.size,
                    file.needsExtractionOfType,
                    block.fileData.payload
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

    companion object : Loggable {
        override val log = logger()
    }
}

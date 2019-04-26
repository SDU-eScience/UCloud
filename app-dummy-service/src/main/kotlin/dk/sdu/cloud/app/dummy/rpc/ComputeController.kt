package dk.sdu.cloud.app.dummy.rpc

import dk.sdu.cloud.app.api.InternalStdStreamsResponse
import dk.sdu.cloud.app.dummy.api.DummyComputeDescriptions
import dk.sdu.cloud.app.dummy.services.ControlService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.io.jvm.javaio.copyTo
import java.io.File

class ComputeController(
    private val controlService: ControlService
) : Controller {
    private val tempDirectory = File("/tmp/app-dummy")

    init {
        tempDirectory.mkdir()
    }

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(DummyComputeDescriptions.cleanup) {
            val id = controlService.saveName(request.id)
            controlService.confirmAction("cleanup", "Cleanup [short id: $id] ${request.id}")
            ok(Unit)
        }

        implement(DummyComputeDescriptions.follow) {
            val id = controlService.saveName(request.job.id)
            controlService.confirmAction("follow", "Follow [short id: $id] ${request.job.id}")
            ok(InternalStdStreamsResponse("", 0, "", 0))
        }

        implement(DummyComputeDescriptions.jobPrepared) {
            val id = controlService.saveName(request.id)
            controlService.confirmAction("jobPrepared", "Job prepared: [short id: $id]")
            ok(Unit)
        }

        implement(DummyComputeDescriptions.jobVerified) {
            val id = controlService.saveName(request.id)
            controlService.confirmAction("jobVerified", "Job verified: [short id: $id]")
            File(tempDirectory, request.id).mkdir()
            ok(Unit)
        }

        implement(DummyComputeDescriptions.submitFile) {
            val id = controlService.saveName(request.jobId)
            controlService.confirmAction("submitFile", "Input file [short id: $id] ${request.jobId}, ${request.parameterName}")
            val jobDir = File(tempDirectory, request.jobId)
            val file = File(jobDir, request.parameterName)
            file.outputStream().use {
                request.fileData.asIngoing().channel.copyTo(it)
            }

            ok(Unit)
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
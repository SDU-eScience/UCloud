package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.SSHKeys
import dk.sdu.cloud.app.orchestrator.api.SSHKeysControl
import dk.sdu.cloud.app.orchestrator.services.SshKeyService
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class SshKeyController(
    private val sshKeys: SshKeyService,
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(SSHKeys.create) {
            ok(BulkResponse(sshKeys.create(actorAndProject, request.items)))
        }

        implement(SSHKeys.delete) {
            sshKeys.delete(actorAndProject, request.items)
            ok(Unit)
        }

        implement(SSHKeys.retrieve) {
            ok(sshKeys.retrieve(actorAndProject, request.id))
        }

        implement(SSHKeys.browse) {
            ok(sshKeys.browse(actorAndProject, request.normalize()))
        }

        implement(SSHKeysControl.browse) {
            ok(sshKeys.retrieveAsProvider(actorAndProject, request.usernames, request.normalize()))
        }

        implement(JobsControl.browseSshKeys) {
            ok(sshKeys.retrieveByJob(actorAndProject, request.jobId, request.normalize()))
        }

        return@with
    }
}
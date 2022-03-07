package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.accounting.services.projects.v2.*

class ProjectsControllerV2(
    private val projects: ProjectService,
): Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Projects.retrieve) {
            ok(projects.retrieve(actorAndProject, request))
        }
    }
}


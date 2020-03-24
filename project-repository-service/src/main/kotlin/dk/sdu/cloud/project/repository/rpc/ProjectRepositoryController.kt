package dk.sdu.cloud.project.repository.rpc

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.project.repository.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.project.repository.services.RepositoryService
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class ProjectRepositoryController(
    private val service: RepositoryService,
    private val userClientFactory: (username: String) -> AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(ProjectRepository.create) {
            service.create(
                ctx.securityPrincipal,
                project,
                request.name
            )

            ok(Unit)
        }

        implement(ProjectRepository.delete) {
            service.delete(
                userClient(),
                project,
                request.name
            )
            ok(Unit)
        }

        implement(ProjectRepository.update) {
            service.update(
                userClient(),
                project,
                request.oldName,
                request.newName
            )
            ok(Unit)
        }

        implement(ProjectRepository.list) {
            service.listRepositories(
                ctx.securityPrincipal,
                project
            )
        }
        return@configure
    }

    private val CallHandler<*, *, *>.project: String
        get() = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)

    private fun CallHandler<*, *, *>.userClient(): AuthenticatedClient {
        return userClientFactory(ctx.securityPrincipal.username)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

package dk.sdu.cloud.project.repository.rpc

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.project.repository.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.project.repository.services.RepositoryService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.paginate
import io.ktor.http.HttpStatusCode

class ProjectRepositoryController(
    private val service: RepositoryService,
    private val userClientFactory: (accessToken: String) -> AuthenticatedClient
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
            ok(
                service.listRepositories(
                    ctx.securityPrincipal,
                    project
                ).paginate(request.normalize())
            )
        }

        implement(ProjectRepository.listFiles) {
            ok(
                service
                    .listRepositories(ctx.securityPrincipal, project)
                    .paginate(request.normalize())
                    .mapItems {
                        StorageFile(
                            FileType.DIRECTORY,
                            "/projects/$project/$it"
                        )
                    }
            )
        }

        implement(ProjectRepository.updatePermissions) {
            service.updatePermissions(userClient(), project, request.repository, request.newAcl)
            ok(Unit)
        }
        return@configure
    }

    private val CallHandler<*, *, *>.project: String
        get() = ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)

    private fun CallHandler<*, *, *>.userClient(): AuthenticatedClient {
        return userClientFactory(ctx.bearer!!)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

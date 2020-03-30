package dk.sdu.cloud.project.repository.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.project.repository.api.*
import dk.sdu.cloud.project.repository.services.RepositoryService
import dk.sdu.cloud.service.*
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
            val username = when(ctx.securityPrincipal.role) {
                in Roles.PRIVILEDGED -> request.user ?: ctx.securityPrincipal.username
                else -> ctx.securityPrincipal.username
            }

            val paging = when {
                ctx.securityPrincipal.role in Roles.PRIVILEDGED && request.itemsPerPage == null
                        && request.page == null -> {
                    null
                }

                else -> request.normalize()
            }

            val listRepositories = service.listRepositories(
                username,
                project
            )

            val page = if (paging == null) {
                Page(listRepositories.size, listRepositories.size, 0, listRepositories)
            } else {
                listRepositories.paginate(paging)
            }

            ok(page)
        }

        implement(ProjectRepository.listFiles) {
            ok(
                service
                    .listRepositories(ctx.securityPrincipal.username, project)
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

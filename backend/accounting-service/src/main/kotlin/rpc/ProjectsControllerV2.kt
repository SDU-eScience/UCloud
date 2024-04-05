package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Role
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.accounting.services.projects.v2.*
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import io.ktor.server.request.*

class ProjectsControllerV2(
    private val projects: ProjectService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Projects.retrieve) {
            val actorRole = (actorAndProject.actor as? Actor.User)?.principal?.role
            val ap = if (actorRole == Role.SERVICE) ActorAndProject.System else actorAndProject
            ok(projects.retrieve(ap, request))
        }

        implement(Projects.browse) {
            ok(projects.browse(actorAndProject, request))
        }

        implement(Projects.create) {
            val pi = if ((actorAndProject.actor as? Actor.User)?.principal?.role == Role.ADMIN) {
                var result: String? = null
                withContext<HttpCall> {
                    result = ctx.call.request.header("principal-investigator")
                }
                result
            } else {
                null
            }
            ok(projects.create(actorAndProject, request, piOverride = pi, addSelfWithPiOverride = true))
        }

        implement(Projects.archive) {
            ok(projects.archive(actorAndProject, request))
        }

        implement(Projects.unarchive) {
            ok(projects.unarchive(actorAndProject, request))
        }

        implement(Projects.toggleFavorite) {
            ok(projects.toggleFavorite(actorAndProject, request))
        }

        implement(Projects.renameProject) {
            ok(projects.renameProject(actorAndProject, request))
        }

        implement(Projects.updateSettings) {
            ok(projects.updateSettings(actorAndProject, request))
        }

        implement(Projects.verifyMembership) {
            ok(projects.verifyMembership(actorAndProject, request))
        }

        implement(Projects.browseInvites) {
            ok(projects.browseInvites(actorAndProject, request))
        }

        implement(Projects.createInvite) {
            ok(projects.createInvite(actorAndProject, request))
        }

        implement(Projects.acceptInvite) {
            ok(projects.acceptInvite(actorAndProject, request))
        }

        implement(Projects.deleteInvite) {
            ok(projects.deleteInvite(actorAndProject, request))
        }

        implement(Projects.createInviteLink) {
            ok(projects.createInviteLink(actorAndProject))
        }

        implement(Projects.browseInviteLinks) {
            ok(projects.browseInviteLinks(actorAndProject))
        }

        implement(Projects.retrieveInviteLinkInfo) {
            ok(projects.retrieveInviteLinkInfo(actorAndProject, request))
        }

        implement(Projects.deleteInviteLink) {
            ok(projects.deleteInviteLink(actorAndProject, request))
        }

        implement(Projects.updateInviteLink) {
            ok(projects.updateInviteLink(actorAndProject, request))
        }

        implement(Projects.acceptInviteLink) {
            ok(projects.acceptInviteLink(actorAndProject, request))
        }

        implement(Projects.deleteMember) {
            ok(projects.deleteMember(actorAndProject, request))
        }

        implement(Projects.changeRole) {
            ok(projects.changeRole(actorAndProject, request))
        }

        implement(Projects.createGroup) {
            ok(projects.createGroup(actorAndProject, request))
        }

        implement(Projects.renameGroup) {
            ok(projects.renameGroup(actorAndProject, request))
        }

        implement(Projects.deleteGroup) {
            ok(projects.deleteGroup(actorAndProject, request))
        }

        implement(Projects.createGroupMember) {
            ok(projects.createGroupMember(actorAndProject, request))
        }

        implement(Projects.deleteGroupMember) {
            ok(projects.deleteGroupMember(actorAndProject, request))
        }

        implement(Projects.retrieveProviderProject) {
            ok(projects.retrieveProviderProject(actorAndProject))
        }

        implement(Projects.retrieveProviderProjectInternal) {
            ok(FindByStringId(projects.retrieveProviderProjectInternal(request.id)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)))
        }

        implement(Projects.retrieveAllUsersGroup) {
            ok(
                BulkResponse(request.items.map {
                    FindByStringId(projects.locateOrCreateAllUsersGroup(it.project))
                })
            )
        }
    }
}

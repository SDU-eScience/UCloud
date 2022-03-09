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

        implement(Projects.browse) {
            ok(projects.browse(actorAndProject, request))
        }

        implement(Projects.create) {
            ok(projects.create(actorAndProject, request))
        }

        implement(Projects.archive) {
            ok(projects.archive(actorAndProject, request))
        }

        implement(Projects.toggleFavorite) {
            ok(projects.toggleFavorite(actorAndProject, request))
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

        implement(Projects.deleteMember) {
            ok(projects.deleteMember(actorAndProject, request))
        }

        implement(Projects.changeRole) {
            ok(projects.changeRole(actorAndProject, request))
        }

        implement(Projects.createGroup) {
            ok(projects.createGroup(actorAndProject, bulkRequestOf(request)))
        }

        implement(Projects.createGroupMember) {
            ok(projects.createGroupMember(actorAndProject, request))
        }

        implement(Projects.deleteGroupMember) {
            ok(projects.deleteGroupMember(actorAndProject, request))
        }
    }
}


package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.services.projects.ProjectException
import dk.sdu.cloud.accounting.services.projects.ProjectService
import dk.sdu.cloud.accounting.services.projects.ProjectQueryService
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*

class ProjectController(
    private val db: DBContext,
    private val projects: ProjectService,
    private val queries: ProjectQueryService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Projects.create) {
            val pi = request.principalInvestigator.takeIf { ctx.securityPrincipal.role in Roles.PRIVILEGED }
            ok(FindByStringId(projects.create(db, ctx.securityPrincipal.toActor(), request.title, request.parent, pi)))
        }

        implement(Projects.invite) {
            projects.inviteMember(db, ctx.securityPrincipal.username, request.projectId, request.usernames)
            ok(Unit)
        }

        implement(Projects.deleteMember) {
            projects.deleteMember(db, ctx.securityPrincipal.username, request.projectId, request.member)
            ok(Unit)
        }

        implement(Projects.exists) {
            val exists = queries.exists(db, request.projectId)
            ok(ExistsResponse(exists))
        }

        implement(Projects.changeUserRole) {
            projects.changeRoleOfMember(
                db,
                ctx.securityPrincipal.username,
                request.projectId,
                request.member,
                request.newRole
            )
            ok(Unit)
        }

        implement(Projects.viewMemberInProject) {
            val role = projects.findRoleOfMember(db, request.projectId, request.username)
                ?: throw ProjectException.NotFound()
            ok(ViewMemberInProjectResponse(ProjectMember(request.username, role)))
        }

        implement(Projects.listFavoriteProjects) {
            val showArchived = request.archived

            val user = when (ctx.securityPrincipal.role) {
                in Roles.PRIVILEGED -> {
                    request.user ?: ctx.securityPrincipal.username
                }
                else -> ctx.securityPrincipal.username
            }

            val result = queries.listFavoriteProjects(db, user, showArchived, request.normalize())
            ok(
                if (request.showAncestorPath == true) {
                    result.withNewItems(queries.addAncestors(db, ctx.securityPrincipal.toActor(), result.items))
                } else {
                    result
                }
            )
        }

        implement(Projects.listProjects) {
            val showArchived = request.archived ?: true
            val noFavorites = request.noFavorites ?: false
            val user = when (ctx.securityPrincipal.role) {
                in Roles.PRIVILEGED -> {
                    request.user ?: ctx.securityPrincipal.username
                }
                else -> ctx.securityPrincipal.username
            }

            val pagination = when {
                request.itemsPerPage == null && request.page == null &&
                        ctx.securityPrincipal.role in Roles.PRIVILEGED -> null

                else -> request.normalize()
            }

            val projects = queries.listProjects(db, user, showArchived, pagination, noFavorites = noFavorites)
            ok(
                if (request.showAncestorPath == true) {
                    projects.withNewItems(queries.addAncestors(db, ctx.securityPrincipal.toActor(), projects.items))
                } else {
                    projects
                }
            )
        }

        implement(Projects.verifyMembership) {
            val project = ctx.project
            if (project == null) {
                ok(Unit)
            } else {
                projects.verifyMembership(db, ctx.securityPrincipal.username, project)
                ok(Unit)
            }
        }

        implement(Projects.acceptInvite) {
            projects.acceptInvite(db, ctx.securityPrincipal.username, request.projectId)
            ok(Unit)
        }

        implement(Projects.rejectInvite) {
            projects.rejectInvite(
                db,
                ctx.securityPrincipal.username,
                request.projectId,
                request.username ?: ctx.securityPrincipal.username
            )

            ok(Unit)
        }

        implement(Projects.leaveProject) {
            projects.leaveProject(
                db,
                ctx.securityPrincipal.username,
                ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)
            )

            ok(Unit)
        }

        implement(Projects.listIngoingInvites) {
            ok(queries.listIngoingInvites(db, ctx.securityPrincipal.username, request.normalize()))
        }

        implement(Projects.listOutgoingInvites) {
            ok(
                queries.listOutgoingInvites(
                    db,
                    ctx.securityPrincipal.username,
                    ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest),
                    request.normalize()
                )
            )
        }

        implement(Projects.transferPiRole) {
            projects.transferPrincipalInvestigatorRole(
                db,
                ctx.securityPrincipal.username,
                ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest),
                request.newPrincipalInvestigator
            )

            ok(Unit)
        }

        implement(Projects.archive) {
            projects.setArchiveStatus(
                db,
                ctx.securityPrincipal.username,
                ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest),
                request.archiveStatus
            )

            ok(Unit)
        }

        implement(Projects.archiveBulk) {
            db.withSession { session ->
                request.projects.forEach {
                    projects.setArchiveStatus(
                        session,
                        ctx.securityPrincipal.username,
                        it.projectId,
                        !it.archived
                    )
                }
            }
            ok(Unit)
        }

        implement(Projects.viewProject) {
            val project = queries.listProjects(
                db,
                ctx.securityPrincipal.username,
                true,
                null,
                request.id,
                false
            ).items.singleOrNull() ?: throw ProjectException.NotFound()

            ok(queries.addAncestors(db, ctx.securityPrincipal.toActor(), listOf(project)).single())
        }

        implement(Projects.listSubProjects) {
            ok(
                queries.listSubProjects(
                    db,
                    request.normalizeWithFullReadEnabled(ctx.securityPrincipal.toActor()),
                    ctx.securityPrincipal.toActor(),
                    ctx.project ?: throw RPCException("No project", HttpStatusCode.BadRequest)
                )
            )
        }

        implement(Projects.countSubProjects) {
            ok(
                queries.subProjectsCount(
                    db,
                    ctx.securityPrincipal,
                    ctx.project ?: throw RPCException("No project", HttpStatusCode.BadRequest)
                )
            )
        }

        implement(Projects.viewAncestors) {
            ok(
                queries.viewAncestors(
                    db,
                    ctx.securityPrincipal.toActor(),
                    ctx.project ?: throw RPCException("No project", HttpStatusCode.BadRequest)
                )
            )
        }

        implement(Projects.lookupByPath) {
            ok(
                queries.lookupByPath(db, request.title) ?: throw RPCException(
                    "No project with that name",
                    HttpStatusCode.BadRequest
                )
            )
        }

        implement(Projects.lookupById) {
            ok(
                queries.lookupById(db, request.id) ?: throw RPCException(
                    "No project with that id",
                    HttpStatusCode.BadRequest
                )
            )
        }

        implement(Projects.lookupByIdBulk) {
            val projects = queries.lookupByIdBulk(db, request.ids)
            if (projects.isEmpty()) {
                throw RPCException("No projects with those ids", HttpStatusCode.NotFound)
            }
            ok(projects)
        }

        implement(Projects.lookupPrincipalInvestigator) {
            ok(
                queries.lookupPrincipalInvestigator(
                    db,
                    ctx.securityPrincipal.toActor(),
                    ctx.project ?: throw RPCException("No project", HttpStatusCode.BadRequest)
                )
            )
        }

        implement(Projects.allowedRenaming) {
            ok(
                AllowsRenamingResponse(projects.allowedRenaming(db, request.projectId, ctx.securityPrincipal.toActor()))
            )
        }

        implement(Projects.allowsSubProjectRenaming) {
            ok(
                AllowsRenamingResponse(projects.allowsSubProjectsRenaming(db, request.projectId, ctx.securityPrincipal.toActor()))
            )
        }

        implement(Projects.toggleRenaming) {
            projects.toggleRenaming(db, request.projectId, ctx.securityPrincipal.toActor())
            ok(Unit)
        }

        implement(Projects.rename) {
            ok(
                projects.renameProject(
                    db,
                    ctx.securityPrincipal.toActor(),
                    request.id,
                    request.newTitle
                )
            )
        }

        implement(Projects.updateDataManagementPlan) {
            projects.updateDataManagementPlan(db, ctx.securityPrincipal.toActor(), request.id, request.dmp)
            ok(Unit)
        }

        implement(Projects.fetchDataManagementPlan) {
            ok(
                FetchDataManagementPlanResponse(
                    projects.fetchDataManagementPlan(
                        db,
                        ctx.securityPrincipal.toActor(),
                        ctx.project ?: throw RPCException("No project", HttpStatusCode.BadRequest)
                    )
                )
            )
        }

        implement(Projects.search) {
            ok(
                queries.searchProjectPaths(
                    db,
                    ctx.securityPrincipal.toActor(),
                    request,
                    request
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

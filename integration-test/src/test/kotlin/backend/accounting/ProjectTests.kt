package dk.sdu.cloud.integration.backend.accounting

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.utils.*
import dk.sdu.cloud.project.api.v2.*

class ProjectTests : IntegrationTest() {

    // Note(Brian): Utility function to get the client of a user with the specified role within the project.
    //  - If role is null, a client for a user which isn't a member of the project is returned,
    //  - if role is PI, a client for the pi of the project is returned,
    //  - otherwise a client for a new user with the specified project role is returned.
    suspend fun clientWithProjectRole(project: NormalProjectInitialization, role: ProjectRole?): AuthenticatedClient {
        return when (role) {
            null -> {
                createUser().client
            }

            ProjectRole.USER -> {
                val (userClient, username) = createUser()
                addMemberToProject(
                    project.projectId,
                    project.piClient,
                    userClient,
                    username,
                    ProjectRole.USER
                )
                userClient
            }

            ProjectRole.ADMIN -> {
                val (userClient, username) = createUser()
                addMemberToProject(
                    project.projectId,
                    project.piClient,
                    userClient,
                    username,
                    ProjectRole.ADMIN
                )
                userClient
            }

            else -> {
                project.piClient
            }
        }
    }


    override fun defineTests() {
        run {
            class In(
                val requestFromRole: ProjectRole?
            )

            class Out(
                val createStatus: HttpStatusCode,
                val browseResponse: List<ProjectInviteLink>
            )

            test<In, Out>("Project invite link create and browse") {
                execute {
                    val provider = createSampleProducts()
                    val root = initializeRootProject(provider.projectId)
                    val project = initializeNormalProject(root)

                    val client = clientWithProjectRole(project, input.requestFromRole)

                    val create = Projects.createInviteLink.call(
                        Unit,
                        client.withProject(project.projectId)
                    ).statusCode

                    val browse = Projects.browseInviteLinks.call(
                        ProjectsBrowseInviteLinksRequest(),
                        client.withProject(project.projectId)
                    ).orThrow()

                    Out(
                        create,
                        browse.items
                    )
                }

                case("by PI (success)") {
                    input(In(ProjectRole.PI))
                    check {
                        assert(output.createStatus == HttpStatusCode.OK)
                        assert(output.browseResponse.size == 1)
                    }
                }

                case("by project admin (success)") {
                    input(In(ProjectRole.ADMIN))
                    check {
                        assert(output.createStatus == HttpStatusCode.OK)
                        assert(output.browseResponse.size == 1)
                    }
                }

                case("by project member (failure)") {
                    input(In(ProjectRole.USER))
                    expectFailure()
                }

                case("by other user (failure)") {
                    input(In(null))
                    expectFailure()
                }
            }
        }

        run {
            class In(
                val requestFromRole: ProjectRole?,
                val updateAssignmentRole: ProjectRole
            )

            class Out(
                val token: String,
                val browse: List<ProjectInviteLink>
            )

            test<In, Out>("Project invite link update role assignment") {
                execute {
                    val provider = createSampleProducts()
                    val root = initializeRootProject(provider.projectId)
                    val project = initializeNormalProject(root)

                    val client = clientWithProjectRole(project, input.requestFromRole)

                    val token = Projects.createInviteLink.call(
                        Unit,
                        client.withProject(project.projectId)
                    ).orThrow().token

                    Projects.updateInviteLink.call(
                        ProjectsUpdateInviteLinkRequest(token, input.updateAssignmentRole, emptyList()),
                        client.withProject(project.projectId),
                    )

                    val browse = Projects.browseInviteLinks.call(
                        ProjectsBrowseInviteLinksRequest(),
                        client.withProject(project.projectId)
                    ).orThrow()

                    Out(
                        token,
                        browse.items
                    )
                }

                case("by PI (success)") {
                    input(In(ProjectRole.PI, ProjectRole.ADMIN))
                    check {
                        assert(output.browse.first { it.token == output.token }.roleAssignment == input.updateAssignmentRole)
                    }
                }

                case("by project admin (success)") {
                    input(In(ProjectRole.ADMIN, ProjectRole.ADMIN))
                    check {
                        assert(output.browse.first { it.token == output.token }.roleAssignment == input.updateAssignmentRole)
                    }
                }

                case("by project member (failure)") {
                    input(In(ProjectRole.USER, ProjectRole.ADMIN))
                    expectFailure()
                }

                case("by other user (failure)") {
                    input(In(null, ProjectRole.ADMIN))
                    expectFailure()
                }
            }
        }
        run {
            class In(
                val requestFromRole: ProjectRole?,
            )

            class Out(
                val token: String,
                val browse: List<ProjectInviteLink>
            )

            test<In, Out>("Project invite link update group assignment") {
                execute {
                    val provider = createSampleProducts()
                    val root = initializeRootProject(provider.projectId)
                    val project = initializeNormalProject(root)

                    val group1 = createGroup(project)
                    val group2 = createGroup(project)

                    val client = clientWithProjectRole(project, input.requestFromRole)

                    val token = Projects.createInviteLink.call(
                        Unit,
                        client.withProject(project.projectId)
                    ).orThrow().token

                    Projects.updateInviteLink.call(
                        ProjectsUpdateInviteLinkRequest(token, ProjectRole.USER, listOf(group1.groupId, group2.groupId)),
                        client.withProject(project.projectId)
                    )

                    val browse = Projects.browseInviteLinks.call(
                        ProjectsBrowseInviteLinksRequest(),
                        client.withProject(project.projectId)
                    ).orThrow()

                    Out(
                        token,
                        browse.items
                    )
                }

                case("by PI (success)") {
                    input(In(ProjectRole.PI))
                    check {
                        assert(output.browse.first { it.token == output.token }.groupAssignment.size == 2)
                    }
                }

                case("by project admin (success)") {
                    input(In(ProjectRole.ADMIN))
                    check {
                        assert(output.browse.first { it.token == output.token }.groupAssignment.size == 2)
                    }
                }

                case("by project member (failure)") {
                    input(In(ProjectRole.USER))
                    expectFailure()
                }

                case("by other user (failure)") {
                    input(In(null))
                    expectFailure()
                }
            }
        }
        run {
            class In()

            class Out(
                val username: String,
                val members: List<ProjectMember>?
            )

            test<In, Out>("Project invite link accept") {
                execute {
                    val provider = createSampleProducts()
                    val root = initializeRootProject(provider.projectId)
                    val project = initializeNormalProject(root)

                    val group1 = createGroup(project)
                    val group2 = createGroup(project)

                    val user = createUser()

                    val token = Projects.createInviteLink.call(
                        Unit,
                        project.piClient.withProject(project.projectId)
                    ).orThrow().token

                    Projects.updateInviteLink.call(
                        ProjectsUpdateInviteLinkRequest(token, ProjectRole.USER, listOf(group1.groupId, group2.groupId)),
                        project.piClient.withProject(project.projectId)
                    )

                    Projects.acceptInviteLink.call(
                        ProjectsAcceptInviteLinkRequest(token),
                        user.client
                    ).orThrow()

                    val projectMembers = Projects.retrieve.call(
                        ProjectsRetrieveRequest(project.projectId, includeMembers = true),
                        project.piClient.withProject(project.projectId)
                    ).orThrow()

                    Out(
                        user.username,
                        projectMembers.status.members
                    )
                }

                case("user added to project") {
                    input(In())
                    check {
                        assert(output.members?.map { it.username }?.contains(output.username) ?: false)
                    }
                }
            }
        }
    }
}
package dk.sdu.cloud.integration.backend.accounting

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.utils.*
import dk.sdu.cloud.project.api.v2.*
import kotlin.test.assertNotEquals

class ProjectTests : IntegrationTest() {
    override fun defineTests() {
        run {
            class In()

            class Out(
                val createStatus: HttpStatusCode,
                val browseStatus: HttpStatusCode,
                val browseResponse: List<ProjectInviteLink>
            )

            test<In, Out>("Project invite link create and browse (PI)") {
                execute {
                    val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                    val project = initializeNormalProject(root)

                    val create = Projects.createInviteLink.call(
                        Unit,
                        project.piClient.withProject(project.projectId)
                    ).statusCode

                    val browse = Projects.browseInviteLinks.call(
                        ProjectsBrowseInviteLinksRequest(),
                        project.piClient.withProject(project.projectId)
                    )

                    Out(
                        create,
                        browse.statusCode,
                        browse.orThrow().items
                    )
                }

                case("PI create and browse") {
                    input(In())
                    check {
                        assert(output.createStatus == HttpStatusCode.OK)
                        assert(output.browseStatus == HttpStatusCode.OK)
                        assert(output.browseResponse.size == 1)
                    }
                }
            }

            test<In, Out>("Project invite link create and browse (Admin)") {
                execute {
                    val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                    val project = initializeNormalProject(root)

                    val (projectAdminClient, projectAdminUsername) = createUser()
                    addMemberToProject(project.projectId, project.piClient, projectAdminClient, projectAdminUsername, ProjectRole.ADMIN)

                    val create = Projects.createInviteLink.call(
                        Unit,
                        projectAdminClient.withProject(project.projectId)
                    ).statusCode

                    val browse = Projects.browseInviteLinks.call(
                        ProjectsBrowseInviteLinksRequest(),
                        projectAdminClient.withProject(project.projectId)
                    )

                    Out(
                        create,
                        browse.statusCode,
                        browse.orThrow().items
                    )
                }

                case("Admin create and browse") {
                    input(In())
                    check {
                        assert(output.createStatus == HttpStatusCode.OK)
                        assert(output.browseStatus == HttpStatusCode.OK)
                        assert(output.browseResponse.size == 1)
                    }
                }
            }

            test<In, Out>("Project invite link create and browse failure (Member)") {
                execute {
                    val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                    val project = initializeNormalProject(root)

                    val (memberClient, memberUsername) = createUser()
                    addMemberToProject(project.projectId, project.piClient, memberClient, memberUsername)

                    val create = Projects.createInviteLink.call(
                        Unit,
                        memberClient.withProject(project.projectId)
                    ).statusCode

                    val browse = Projects.browseInviteLinks.call(
                        ProjectsBrowseInviteLinksRequest(),
                        memberClient.withProject(project.projectId)
                    )

                    Out(
                        create,
                        browse.statusCode,
                        emptyList()
                    )
                }

                case("Member create and browse fail") {
                    input(In())
                    check {
                        assertNotEquals(output.createStatus, HttpStatusCode.OK)
                        assertNotEquals(output.browseStatus, HttpStatusCode.OK)
                    }
                }
            }

            test<In, Out>("Project invite link create and browse failure (Other user)") {
               execute {
                   val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                   val project = initializeNormalProject(root)

                   val (otherUserClient, _) = createUser()

                   val create = Projects.createInviteLink.call(
                       Unit,
                       otherUserClient.withProject(project.projectId)
                   ).statusCode

                   val browse = Projects.browseInviteLinks.call(
                       ProjectsBrowseInviteLinksRequest(),
                       otherUserClient.withProject(project.projectId)
                   )

                   Out(
                       create,
                       browse.statusCode,
                       emptyList()
                   )
               }

                case("Other user create and browse fail") {
                    input(In())
                    check {
                        assertNotEquals(output.createStatus, HttpStatusCode.OK)
                        assertNotEquals(output.browseStatus, HttpStatusCode.OK)
                    }
                }
            }
        }

        run {
            class In(
                val role: ProjectRole
            )
            class Out(
                val token: String,
                val browse: List<ProjectInviteLink>
            )

            test<In, Out>("Project invite link update role assignment") {
                execute {
                    val root = initializeRootProject(setOf(UCLOUD_PROVIDER))
                    val project = initializeNormalProject(root)

                    val token = Projects.createInviteLink.call(
                        Unit,
                        project.piClient.withProject(project.projectId)
                    ).orThrow().token

                    Projects.updateInviteLinkRoleAssignment.call(
                        ProjectsUpdateInviteLinkRoleAssignmentRequest(token, input.role),
                        project.piClient.withProject(project.projectId)
                    )

                    val browse = Projects.browseInviteLinks.call(
                        ProjectsBrowseInviteLinksRequest(),
                        project.piClient.withProject(project.projectId)
                    ).orThrow()

                    Out(
                        token,
                        browse.items
                    )
                }

                case("successful role assignment") {
                    input(In(ProjectRole.ADMIN))
                    check {
                        assert(output.browse.first { it.token == output.token }.roleAssignment == input.role)
                    }
                }
            }
        }
    }
}
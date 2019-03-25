package dk.sdu.cloud.project.http

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.LookupUsersResponse
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.auth.api.UserLookup
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.project.api.AddMemberRequest
import dk.sdu.cloud.project.api.AddMemberResponse
import dk.sdu.cloud.project.api.ChangeUserRoleRequest
import dk.sdu.cloud.project.api.ChangeUserRoleResponse
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.CreateProjectResponse
import dk.sdu.cloud.project.api.DeleteMemberRequest
import dk.sdu.cloud.project.api.DeleteMemberResponse
import dk.sdu.cloud.project.api.DeleteProjectResponse
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.ViewMemberInProjectResponse
import dk.sdu.cloud.project.api.ViewProjectResponse
import dk.sdu.cloud.project.services.ProjectHibernateDao
import dk.sdu.cloud.project.services.ProjectService
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.KtorApplicationTestContext
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.parseSuccessful
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceTest {
    private fun KtorApplicationTestContext.createProject(
        title: String = "Project",
        pi: String = TestUsers.user.username
    ): CreateProjectResponse {
        return sendJson(
            HttpMethod.Post,
            "/api/projects",
            CreateProjectRequest(title, pi),
            TestUsers.admin
        ).parseSuccessful()
    }

    private fun KtorApplicationTestContext.viewProject(
        project: String,
        principalInvestigator: SecurityPrincipal
    ): ViewProjectResponse {
        return sendRequest(
            HttpMethod.Get,
            "/api/projects",
            user = principalInvestigator,
            params = mapOf(
                "id" to project
            )
        ).parseSuccessful()
    }

    private fun KtorApplicationTestContext.deleteProject(
        project: String,
        user: SecurityPrincipal
    ): DeleteProjectResponse {
        return sendRequest(
            HttpMethod.Delete,
            "/api/projects",
            user = user,
            params = mapOf(
                "id" to project
            )
        ).parseSuccessful()
    }

    private fun KtorApplicationTestContext.addMember(
        project: String,
        memberToAdd: ProjectMember,
        principalInvestigator: SecurityPrincipal
    ) {
        sendJson(
            HttpMethod.Post,
            "/api/projects/members",
            AddMemberRequest(project, memberToAdd),
            principalInvestigator
        ).parseSuccessful<AddMemberResponse>()
    }

    private fun KtorApplicationTestContext.deleteMember(
        project: String,
        memberToDelete: String,
        principalInvestigator: SecurityPrincipal
    ): DeleteMemberResponse {
        return sendJson(
            HttpMethod.Delete,
            "/api/projects/members",
            DeleteMemberRequest(project, memberToDelete),
            principalInvestigator
        ).parseSuccessful()
    }

    private fun KtorApplicationTestContext.viewMemberInProject(
        user: SecurityPrincipal,
        project: String,
        username: String
    ): ViewMemberInProjectResponse {
        return sendRequest(
            HttpMethod.Get,
            "/api/projects/members",
            user = user,
            params = mapOf(
                "projectId" to project,
                "username" to username
            )
        ).parseSuccessful()
    }

    private fun KtorApplicationTestContext.mockUsersExists(
        users: List<SecurityPrincipal>
    ) {
        val userLookup = users.associateBy { it.username }
        ClientMock.mockCall(UserDescriptions.lookupUsers) { req ->
            TestCallResult.Ok(
                LookupUsersResponse(req.users.map {
                    val role = userLookup[it]?.role
                    it to (if (role != null) UserLookup(it, 0L, role) else null)
                }.toMap())
            )
        }
    }

    private fun configureMicro(): Micro.() -> Unit {
        return {
            install(HibernateFeature)
        }
    }

    private fun setupServer(): KtorApplicationTestSetupContext.() -> List<ProjectController> {
        return {
            listOf(
                ProjectController(
                    ProjectService(
                        micro.hibernateDatabase,
                        ProjectHibernateDao(),
                        micro.eventStreamService.createProducer(ProjectEvents.events),
                        ClientMock.authenticatedClient
                    )
                )
            )
        }
    }

    @Test
    fun `test creating a project`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                mockUsersExists(listOf(TestUsers.user))
                createProject()
            }
        )
    }

    @Test
    fun `test creating a project (critical failure in auth)`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                ClientMock.mockCallError(
                    UserDescriptions.lookupUsers,
                    statusCode = HttpStatusCode.InternalServerError
                )
                val exception = runCatching { createProject() }.exceptionOrNull() as RPCException
                assertEquals(HttpStatusCode.InternalServerError, exception.httpStatusCode)
            }
        )
    }

    @Test
    fun `test add and view`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.user2
                mockUsersExists(listOf(principalInvestigator, userToAdd))

                val project = createProject(pi = principalInvestigator.username).id

                val view1 = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view1, { it.members.size }, 1)
                assertThatProperty(view1, { it.members }, matcher = {
                    val member = it.single()
                    member.role == ProjectRole.PI && member.username == principalInvestigator.username
                })

                val memberToAdd = ProjectMember(userToAdd.username, ProjectRole.USER)
                addMember(project, memberToAdd, principalInvestigator)

                val view2 = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view2, { it.members.size }, 2)
                assertThatProperty(view2, { it.members }, matcher = {
                    it.any { member -> member.role == ProjectRole.PI && member.username == principalInvestigator.username }
                })
                assertThatProperty(view2, { it.members }, matcher = { it.contains(memberToAdd) })
            }
        )
    }

    @Test
    fun `test add and view member`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.user2
                mockUsersExists(listOf(principalInvestigator, userToAdd))

                val project = createProject(pi = principalInvestigator.username).id
                val memberToAdd = ProjectMember(userToAdd.username, ProjectRole.USER)
                addMember(project, memberToAdd, principalInvestigator)

                val viewMember = viewMemberInProject(TestUsers.admin, project, memberToAdd.username)
                assertEquals(memberToAdd, viewMember.member)
            }
        )
    }

    @Test
    fun `test view member (not found)`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.user2
                mockUsersExists(listOf(principalInvestigator, userToAdd))

                val project = createProject(pi = principalInvestigator.username).id

                val addException = runCatching {
                    viewMemberInProject(
                        TestUsers.admin,
                        project,
                        userToAdd.username
                    )
                }.exceptionOrNull()!! as RPCException

                assertEquals(HttpStatusCode.NotFound, addException.httpStatusCode)
            }
        )
    }

    @Test
    fun `test delete member`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.user2
                mockUsersExists(listOf(principalInvestigator, userToAdd))

                val project = createProject(pi = principalInvestigator.username).id
                val memberToAdd = ProjectMember(userToAdd.username, ProjectRole.USER)
                addMember(project, memberToAdd, principalInvestigator)
                deleteMember(project, memberToAdd.username, principalInvestigator)

                val viewMemberException = runCatching {
                    viewMemberInProject(
                        TestUsers.admin,
                        project,
                        memberToAdd.username
                    )
                }.exceptionOrNull() as RPCException
                assertEquals(HttpStatusCode.NotFound, viewMemberException.httpStatusCode)
            }
        )
    }

    @Test
    fun `test delete bad member`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.user2
                mockUsersExists(listOf(principalInvestigator, userToAdd))

                val project = createProject(pi = principalInvestigator.username).id
                val memberToAdd = ProjectMember(userToAdd.username, ProjectRole.USER)
                val deleteException = runCatching {
                    deleteMember(
                        project,
                        memberToAdd.username,
                        principalInvestigator
                    )
                }.exceptionOrNull() as RPCException
                assertEquals(HttpStatusCode.NotFound, deleteException.httpStatusCode)
            }
        )
    }

    @Test
    fun `test create and delete`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                mockUsersExists(listOf(principalInvestigator))
                val projectId = createProject(pi = principalInvestigator.username).id
                deleteProject(projectId, TestUsers.admin)
            }
        )
    }

    @Test
    fun `test create and delete (invalid user)`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                mockUsersExists(listOf(principalInvestigator))
                val projectId = createProject(pi = principalInvestigator.username).id
                val status =
                    runCatching { deleteProject(projectId, TestUsers.user3) }.exceptionOrNull()!! as RPCException
                assertEquals(HttpStatusCode.Unauthorized, status.httpStatusCode)
            }
        )
    }

    @Test
    fun `test delete (bad project)`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val status =
                    runCatching { deleteProject("notaproject", TestUsers.admin) }.exceptionOrNull()!! as RPCException
                assertEquals(HttpStatusCode.NotFound, status.httpStatusCode)
            }
        )
    }

    @Test
    fun `test add user (does not exist)`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.user2
                mockUsersExists(listOf(principalInvestigator))

                val project = createProject(pi = principalInvestigator.username).id

                val memberToAdd = ProjectMember(userToAdd.username, ProjectRole.USER)
                val memberException = runCatching {
                    addMember(
                        project,
                        memberToAdd,
                        principalInvestigator
                    )
                }.exceptionOrNull()!! as RPCException
                assertEquals(HttpStatusCode.BadRequest, memberException.httpStatusCode)

                val view = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view, { it.members.size }, 1)
                assertThatProperty(view, { it.members }, matcher = {
                    it.any { member -> member.role == ProjectRole.PI && member.username == principalInvestigator.username }
                })
            }
        )
    }

    @Test
    fun `test add user (bad user)`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.service
                mockUsersExists(listOf(principalInvestigator, userToAdd))

                val project = createProject(pi = principalInvestigator.username).id

                val memberToAdd = ProjectMember(userToAdd.username, ProjectRole.USER)
                val memberException = runCatching {
                    addMember(
                        project,
                        memberToAdd,
                        principalInvestigator
                    )
                }.exceptionOrNull()!! as RPCException
                assertEquals(HttpStatusCode.BadRequest, memberException.httpStatusCode)

                val view = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view, { it.members.size }, 1)
                assertThatProperty(view, { it.members }, matcher = {
                    it.any { member -> member.role == ProjectRole.PI && member.username == principalInvestigator.username }
                })
            }
        )
    }

    @Test
    fun `test add duplicate`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.user2
                mockUsersExists(listOf(principalInvestigator, userToAdd))

                val project = createProject(pi = principalInvestigator.username).id

                val view1 = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view1, { it.members.size }, 1)
                assertThatProperty(view1, { it.members }, matcher = {
                    val member = it.single()
                    member.role == ProjectRole.PI && member.username == principalInvestigator.username
                })

                val memberToAdd = ProjectMember(userToAdd.username, ProjectRole.USER)
                addMember(project, memberToAdd, principalInvestigator)

                val view2 = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view2, { it.members.size }, 2)
                assertThatProperty(view2, { it.members }, matcher = {
                    it.any { member -> member.role == ProjectRole.PI && member.username == principalInvestigator.username }
                })
                assertThatProperty(view2, { it.members }, matcher = { it.contains(memberToAdd) })

                val addException = runCatching {
                    addMember(
                        project,
                        memberToAdd,
                        principalInvestigator
                    )
                }.exceptionOrNull()!! as RPCException

                assertEquals(HttpStatusCode.Conflict, addException.httpStatusCode)

                val view3 = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view3, { it.members.size }, 2)
                assertThatProperty(view3, { it.members }, matcher = {
                    it.any { member -> member.role == ProjectRole.PI && member.username == principalInvestigator.username }
                })
                assertThatProperty(view3, { it.members }, matcher = { it.contains(memberToAdd) })
            }
        )
    }

    @Test
    fun `test promoting a user`() {
        withKtorTest(
            microConfigure = configureMicro(),
            setup = setupServer(),
            test = {
                val principalInvestigator = TestUsers.user
                val userToAdd = TestUsers.user2
                mockUsersExists(listOf(principalInvestigator, userToAdd))

                val project = createProject(pi = principalInvestigator.username).id

                val view1 = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view1, { it.members.size }, 1)
                assertThatProperty(view1, { it.members }, matcher = {
                    val member = it.single()
                    member.role == ProjectRole.PI && member.username == principalInvestigator.username
                })

                val memberToAdd = ProjectMember(userToAdd.username, ProjectRole.USER)
                val newRole = ProjectRole.DATA_STEWARD
                addMember(project, memberToAdd, principalInvestigator)
                sendJson(
                    HttpMethod.Post,
                    "/api/projects/members/change-role",
                    ChangeUserRoleRequest(project, memberToAdd.username, newRole),
                    TestUsers.user
                ).parseSuccessful<ChangeUserRoleResponse>()

                val view2 = viewProject(project, principalInvestigator)
                assertThatPropertyEquals(view2, { it.members.size }, 2)
                assertThatProperty(view2, { it.members }, matcher = {
                    it.any { member -> member.role == ProjectRole.PI && member.username == principalInvestigator.username }
                })
                assertThatProperty(view2, { it.members }, matcher = { it.contains(memberToAdd.copy(role = newRole)) })
            }
        )
    }
}

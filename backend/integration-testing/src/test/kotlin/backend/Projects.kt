package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.SetBalanceRequest
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.GiB
import dk.sdu.cloud.file.api.PiB
import dk.sdu.cloud.file.api.TiB
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.favorite.api.ProjectFavorites
import dk.sdu.cloud.project.favorite.api.ToggleFavoriteRequest
import dk.sdu.cloud.service.test.assertThatInstance
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

suspend fun initializeRootProject(
    initializeWallet: Boolean = true,
    amount: Long = 10_000_000.DKK
): String {
    if (initializeWallet) {
        createSampleProducts()
    }

    val id = Projects.create.call(
        CreateProjectRequest("UCloud", null),
        serviceClient
    ).orThrow().id

    Grants.setEnabledStatus.call(
        SetEnabledStatusRequest(
            id,
            true
        ),
        serviceClient
    ).orThrow()

    if (initializeWallet) {
        initializeWallets(id, amount)
        setProjectQuota(id, 1.PiB)
    }

    return id
}

suspend fun initializePersonalWallets(username: String, rootProject: String, amount: Long = 10_000.DKK) {
    sampleProducts.forEach { product ->
        addFundsToPersonalProject(rootProject, username, product = product.category)
    }
}

suspend fun initializeAllPersonalFunds(username: String, rootProject: String) {
    initializePersonalWallets(username, rootProject)
    setPersonalQuota(rootProject, username, 10.GiB)
}

suspend fun initializeWallets(projectId: String, amount: Long = 1_000_000 * 10_000_000L) {
    sampleProducts.forEach { product ->
        Wallets.setBalance.call(
            SetBalanceRequest(
                Wallet(projectId, WalletOwnerType.PROJECT, product.category),
                0L,
                amount
            ),
            serviceClient
        ).orThrow()
    }
}

data class GroupInitialization(
    val groupId: String,
    val groupName: String
)

suspend fun createGroup(
    project: NormalProjectInitialization,
    members: Set<String> = emptySet(),
    groupName: String = "group${Random.nextLong()}"
): GroupInitialization {
    val groupId = ProjectGroups.create.call(
        CreateGroupRequest(groupName),
        project.piClient.withProject(project.projectId)
    ).orThrow().id

    for (member in members) {
        ProjectGroups.addGroupMember.call(
            AddGroupMemberRequest(groupId, member),
            project.piClient.withProject(project.projectId)
        ).orThrow()
    }

    return GroupInitialization(groupId, groupName)
}

data class NormalProjectInitialization(
    val piClient: AuthenticatedClient,
    val piUsername: String,
    val projectId: String
)

suspend fun initializeNormalProject(
    rootProject: String,
    initializeWallet: Boolean = true,
    amount: Long = 10_000_000.DKK
): NormalProjectInitialization {
    val (piClient, piUsername) = createUser()

    Projects.invite.call(
        InviteRequest(rootProject, setOf(piUsername)),
        serviceClient
    ).orThrow()

    Projects.acceptInvite.call(
        AcceptInviteRequest(rootProject),
        piClient
    ).orThrow()

    Projects.changeUserRole.call(
        ChangeUserRoleRequest(rootProject, piUsername, ProjectRole.ADMIN),
        serviceClient
    ).orThrow()

    val newProject = Projects.create.call(
        CreateProjectRequest("Project Title", rootProject),
        piClient
    ).orThrow().id

    Projects.deleteMember.call(
        DeleteMemberRequest(rootProject, piUsername),
        serviceClient
    ).orThrow()

    if (initializeWallet) {
        initializeWallets(newProject, amount)
    }

    return NormalProjectInitialization(piClient, piUsername, newProject)
}

suspend fun addMemberToProject(
    projectId: String,
    adminClient: AuthenticatedClient,
    userClient: AuthenticatedClient,
    username: String
) {
    Projects.invite.call(
        InviteRequest(projectId, setOf(username)),
        adminClient
    ).orThrow()

    Projects.acceptInvite.call(
        AcceptInviteRequest(projectId),
        userClient
    ).orThrow()

}

class ProjectTests : IntegrationTest() {
    @Test
    fun `initialization of root project`() = t {
        val rootId = initializeRootProject()
        val view = Projects.viewProject.call(
            ViewProjectRequest(rootId),
            serviceClient
        ).orThrow()

        assertEquals(rootId, view.projectId)
    }

    @Test
    fun `double initialization fails`() = t {
        initializeRootProject()
        try {
            initializeRootProject()
        } catch (ex: Throwable) {
            // Expected
            return@t
        }
        assertTrue(false)
    }

    @Test
    fun `invite a normal user to the root project`() = t {
        val root = initializeRootProject()
        val (userClient, username) = createUser()

        Projects.invite.call(
            InviteRequest(root, setOf(username)),
            serviceClient
        ).orThrow()

        run {
            val outgoingInvites = Projects.listOutgoingInvites.call(
                ListOutgoingInvitesRequest(),
                serviceClient.withProject(root)
            ).orThrow()

            assertThatInstance(outgoingInvites, "has an outgoing invite") {
                it.items.single().username == username
            }

            val ingoingInvites = Projects.listIngoingInvites.call(
                ListIngoingInvitesRequest(),
                userClient
            ).orThrow()

            assertThatInstance(ingoingInvites, "has an ingoing invite") {
                it.items.single().project == root
            }
        }

        Projects.acceptInvite.call(
            AcceptInviteRequest(root),
            userClient
        ).orThrow()

        run {
            val outgoingInvites = Projects.listOutgoingInvites.call(
                ListOutgoingInvitesRequest(),
                serviceClient.withProject(root)
            ).orThrow()

            assertThatInstance(outgoingInvites, "has no outgoing invites") {
                it.items.isEmpty()
            }

            val ingoingInvites = Projects.listIngoingInvites.call(
                ListIngoingInvitesRequest(),
                userClient
            ).orThrow()

            assertThatInstance(ingoingInvites, "has no ingoing invites") {
                it.items.isEmpty()
            }
        }

        val viewAsUser = Projects.viewProject.call(
            ViewProjectRequest(root),
            userClient
        ).orThrow()

        assertThatInstance(viewAsUser, "is a normal user") {
            it.whoami.username == username && it.whoami.role == ProjectRole.USER
        }
    }

    @Test
    fun `create subproject as normal pi user`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)

        val title = "New Subproject"
        Projects.create.call(
            CreateProjectRequest(title, project.projectId),
            project.piClient
        ).orThrow()

        val subprojects = Projects.listSubProjects.call(
            ListSubProjectsRequest(),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(subprojects, "has a single subproject") {
            it.items.single().title == title
        }
    }

    @Test
    fun `test rejecting an invite`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val (newUserClient, newUser) = createUser()

        Projects.invite.call(
            InviteRequest(project.projectId, setOf(newUser)),
            project.piClient
        ).orThrow()

        Projects.rejectInvite.call(
            RejectInviteRequest(null, project.projectId),
            newUserClient
        ).orThrow()

        val outgoing = Projects.listOutgoingInvites.call(
            ListOutgoingInvitesRequest(),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(outgoing, "has no outgoing invites") {
            it.items.isEmpty()
        }

        val ingoing = Projects.listIngoingInvites.call(
            ListIngoingInvitesRequest(),
            newUserClient
        ).orThrow()

        assertThatInstance(ingoing, "has no ingoing invites") {
            it.items.isEmpty()
        }
    }

    @Test
    fun `test inviting twice fails`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val (_, newUser) = createUser()

        Projects.invite.call(
            InviteRequest(project.projectId, setOf(newUser)),
            project.piClient
        ).orThrow()

        val resp = Projects.invite.call(
            InviteRequest(project.projectId, setOf(newUser)),
            project.piClient
        )

        assertThatInstance(resp, "was a conflict") {
            it.statusCode == HttpStatusCode.Conflict
        }
    }

    @Test
    fun `transfer pi role back and forth`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val (newUserClient, newUser) = createUser()

        addMemberToProject(project.projectId, project.piClient, newUserClient, newUser)

        Projects.transferPiRole.call(
            TransferPiRoleRequest(newUser),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            Projects.viewProject.call(
                ViewProjectRequest(project.projectId),
                project.piClient
            ).orThrow(),
            "is no longer a PI"
        ) {
            it.whoami.role == ProjectRole.ADMIN
        }

        assertThatInstance(
            Projects.viewProject.call(
                ViewProjectRequest(project.projectId),
                newUserClient
            ).orThrow(),
            "is now a PI"
        ) {
            it.whoami.role == ProjectRole.PI
        }

        Projects.transferPiRole.call(
            TransferPiRoleRequest(project.piUsername),
            newUserClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            Projects.viewProject.call(
                ViewProjectRequest(project.projectId),
                project.piClient
            ).orThrow(),
            "is a PI again"
        ) {
            it.whoami.role == ProjectRole.PI
        }

        assertThatInstance(
            Projects.viewProject.call(
                ViewProjectRequest(project.projectId),
                newUserClient
            ).orThrow(),
            "is now an admin"
        ) {
            it.whoami.role == ProjectRole.ADMIN
        }
    }

    @Test
    fun `test that pi transfers require a project member`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val (_, newUser) = createUser()

        assertThatInstance(
            Projects.transferPiRole.call(
                TransferPiRoleRequest(newUser),
                project.piClient.withProject(project.projectId)
            ),
            "was unsuccessful"
        ) { !it.statusCode.isSuccess() }

        assertThatInstance(
            Projects.transferPiRole.call(
                TransferPiRoleRequest("notauser"),
                project.piClient.withProject(project.projectId)
            ),
            "was unsuccessful"
        ) { !it.statusCode.isSuccess() }
    }

    @Test
    fun `test that pi transfers require the pi`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val (newUserClient, newUser) = createUser()

        assertThatInstance(
            Projects.transferPiRole.call(
                TransferPiRoleRequest(project.piUsername),
                newUserClient.withProject(project.projectId)
            ),
            "was unsuccessful"
        ) { !it.statusCode.isSuccess() }


        addMemberToProject(project.projectId, project.piClient, newUserClient, newUser)

        assertThatInstance(
            Projects.transferPiRole.call(
                TransferPiRoleRequest(project.piUsername),
                newUserClient.withProject(project.projectId)
            ),
            "was unsuccessful"
        ) { !it.statusCode.isSuccess() }
    }

    @Test
    fun `test leaving a project`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val (newUserClient, newUser) = createUser()

        assertThatInstance(
            Projects.leaveProject.call(
                LeaveProjectRequest,
                newUserClient.withProject(project.projectId)
            ),
            "was unsuccessful when not a member"
        ) { !it.statusCode.isSuccess() }

        addMemberToProject(project.projectId, project.piClient, newUserClient, newUser)

        Projects.leaveProject.call(
            LeaveProjectRequest,
            newUserClient.withProject(project.projectId)
        ).orThrow()

        assertThatInstance(
            Projects.leaveProject.call(
                LeaveProjectRequest,
                newUserClient.withProject(project.projectId)
            ),
            "was unsuccessful when not a member"
        ) { !it.statusCode.isSuccess() }
    }

    @Test
    fun `test project favorites`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)

        assertThatInstance(
            Projects.listFavoriteProjects.call(
                ListFavoriteProjectsRequest(null, 50, 0, false),
                project.piClient
            ).orThrow(),
            "has no favorites initially"
        ) { it.items.isEmpty() }

        ProjectFavorites.toggleFavorite.call(
            ToggleFavoriteRequest(project.projectId),
            project.piClient
        ).orThrow()

        assertThatInstance(
            Projects.listFavoriteProjects.call(
                ListFavoriteProjectsRequest(null, 50, 0, false),
                project.piClient
            ).orThrow(),
            "has no favorites initially"
        ) { it.items.single().projectId == project.projectId }
    }

    @Test
    fun `test project exists`() = t {
        val root = initializeRootProject()
        val (newUserClient) = createUser()

        Projects.exists.call(
            ExistsRequest(root),
            serviceClient
        ).orThrow()

        assertThatInstance(
            Projects.exists.call(ExistsRequest("not a project"), serviceClient).orThrow(),
            "fails because project does not exist"
        ) { !it.exists }

        assertThatInstance(
            Projects.exists.call(ExistsRequest("not a project"), newUserClient),
            "fails because users cannot use this endpoint"
        ) { !it.statusCode.isSuccess() }
    }

    @Test
    fun `view ancestor test`() = t {
        val root = initializeRootProject()
        val project = initializeNormalProject(root)
        val subProjects = ('a'..'z').map { it.toString() }
        val parents = ArrayList<String>()
        parents.add(root)
        parents.add(project.projectId)
        for (child in subProjects) {
            val newParent = Projects.create.call(
                CreateProjectRequest(child, parents.last()),
                project.piClient
            ).orThrow().id
            parents.add(newParent)
        }

        for (i in 2..(parents.lastIndex)) {
            val child = parents[i]
            val ancestors = Projects.viewAncestors.call(
                ViewAncestorsRequest,
                project.piClient.withProject(child)
            ).orThrow().map { it.id }

            assertEquals(parents.subList(1, i + 1), ancestors)
        }
    }
}

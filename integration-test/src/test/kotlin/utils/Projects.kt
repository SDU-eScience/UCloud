package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.adminUsername
import dk.sdu.cloud.integration.serviceClient
import dk.sdu.cloud.project.api.v2.*
import kotlin.random.Random

data class GroupInitialization(
    val groupId: String,
    val groupName: String
)

suspend fun createGroup(
    project: NormalProjectInitialization,
    members: Set<String> = emptySet(),
    groupName: String = "group${Random.nextLong()}"
): GroupInitialization {
    val groupId = Projects.createGroup.call(
        bulkRequestOf(Group.Specification(project.projectId, groupName)),
        project.piClient
    ).orThrow().responses.single().id

    if (members.isNotEmpty()) {
        Projects.createGroupMember.call(
            BulkRequest(
                members.map { mem ->
                    GroupMember(mem, groupId)
                }
            ),
            serviceClient
        ).orThrow()
    }

    return GroupInitialization(groupId, groupName)
}

data class NormalProjectInitialization(
    val piClient: AuthenticatedClient,
    val piUsername: String,
    val projectId: String
)

suspend fun initializeRootProject(
    providers: Set<String>,
    admin: AuthenticatedClient? = null
): String {
    val adminClient = admin ?: adminClient
    val rootProject = Projects.create.call(
        bulkRequestOf(
            Project.Specification(
                parent = null,
                title = generateId("root")
            )
        ),
        adminClient
    ).orThrow().responses.single().id

    val products = findProductCategories(providers)

    Accounting.rootDeposit.call(
        BulkRequest(
            products.map { category ->
                RootDepositRequestItem(
                    category,
                    WalletOwner.Project(rootProject),
                    10_000_000_000L,
                    "Root deposit"
                )
            }
        ),
        adminClient
    ).orThrow()

    return rootProject
}

suspend fun initializeNormalProject(
    rootProject: String,
    initializeWallet: Boolean = true,
    amount: Long = 10_000_000_000,
    userRole: Role = Role.USER,
    admin: CreatedUser? = null
): NormalProjectInitialization {
    val adminClient = admin?.client ?: adminClient
    val adminUsername = admin?.username ?: adminUsername
    val (piClient, piUsername) = createUser(username = "pi-${Random.nextLong()}", role = userRole)

    val projectId = Projects.create.call(
        bulkRequestOf(
            Project.Specification(
                parent = rootProject,
                title = generateId("normal")
            )
        ),
        adminClient
    ).orThrow().responses.single().id

    Projects.createInvite.call(
        bulkRequestOf(
            ProjectsCreateInviteRequestItem(piUsername)
        ),
        adminClient.withProject(projectId)
    ).orThrow()

    Projects.acceptInvite.call(
        bulkRequestOf(FindByProjectId(projectId)),
        piClient
    ).orThrow()

    Projects.changeRole.call(
        bulkRequestOf(ProjectsChangeRoleRequestItem(piUsername, ProjectRole.PI)),
        adminClient.withProject(projectId)
    ).orThrow()

    Projects.deleteMember.call(
        bulkRequestOf(ProjectsDeleteMemberRequestItem(adminUsername)),
        adminClient.withProject(projectId)
    ).orThrow()

    if (initializeWallet) {
        Accounting.deposit.call(
            BulkRequest(
                findAllocations(rootProject, adminClient).map { alloc ->
                    DepositToWalletRequestItem(
                        WalletOwner.Project(projectId),
                        alloc.id,
                        amount,
                        "wallet init"
                    )
                }
            ),
            adminClient
        ).orThrow()
    }

    return NormalProjectInitialization(piClient, piUsername, projectId)
}

suspend fun addMemberToProject(
    projectId: String,
    adminClient: AuthenticatedClient,
    userClient: AuthenticatedClient,
    username: String,
    role: ProjectRole = ProjectRole.USER
) {
    Projects.createInvite.call(
        bulkRequestOf(
            ProjectsCreateInviteRequestItem(username)
        ),
        adminClient.withProject(projectId)
    ).orThrow()

    Projects.acceptInvite.call(
        bulkRequestOf(FindByProjectId(projectId)),
        userClient
    ).orThrow()

    if (role != ProjectRole.USER) {
        Projects.changeRole.call(
            bulkRequestOf(
                ProjectsChangeRoleRequestItem(username, role)
            ),
            adminClient.withProject(projectId)
        ).orThrow()
    }
}

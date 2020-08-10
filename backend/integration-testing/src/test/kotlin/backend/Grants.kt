package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.RetrieveBalanceRequest
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.project.api.ChangeUserRoleRequest
import dk.sdu.cloud.project.api.ListProjectsRequest
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.test.assertThatInstance
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.junit.Test
import kotlin.test.assertEquals

suspend fun enableGrants(projectId: String) {
    assert(!Grants.isEnabled.call(IsEnabledRequest(projectId), serviceClient).orThrow().enabled)

    Grants.setEnabledStatus.call(
        SetEnabledStatusRequest(projectId, true),
        serviceClient
    ).orThrow()

    assert(Grants.isEnabled.call(IsEnabledRequest(projectId), serviceClient).orThrow().enabled)
}

suspend fun allowGrantsFrom(projectId: String, piClient: AuthenticatedClient, criteria: UserCriteria) {
    val currentSettings = Grants.readRequestSettings.call(
        ReadRequestSettingsRequest(projectId),
        piClient
    ).orThrow()

    Grants.uploadRequestSettings.call(
        UploadRequestSettingsRequest(
            currentSettings.automaticApproval,
            currentSettings.allowRequestsFrom + listOf(criteria)
        ),
        piClient.withProject(projectId)
    ).orThrow()
}

suspend fun submitGrantApplication(
    projectId: String,
    username: String,
    client: AuthenticatedClient,
    recipient: GrantRecipient = GrantRecipient.PersonalProject(username),
    resources: List<ResourceRequest> = listOf(ResourceRequest.fromProduct(sampleCompute, 1000.DKK))
): Long {
    // Check that we can read templates
    Grants.readTemplates.call(
        ReadTemplatesRequest(projectId),
        client
    ).orThrow()

    val id = Grants.submitApplication.call(
        SubmitApplicationRequest(projectId, recipient, "", resources),
        client
    ).orThrow().id

    Grants.viewApplication.call(
        ViewApplicationRequest(id),
        client
    ).orThrow()

    assertThatInstance(
        Grants.outgoingApplications.call(
            OutgoingApplicationsRequest(itemsPerPage = 100),
            client
        ).orThrow(),
        "should contain our application"
    ) { it.items.any { it.id == id } }

    return id
}

suspend fun approveGrantApplication(
    application: Long,
    piClient: AuthenticatedClient,
    recipientClient: AuthenticatedClient
) {
    val appBefore = Grants.viewApplication.call(ViewApplicationRequest(application), piClient).orThrow()
    assertEquals(ApplicationStatus.IN_PROGRESS, appBefore.application.status)
    Grants.approveApplication.call(ApproveApplicationRequest(application), piClient).orThrow()
    val appAfter = Grants.viewApplication.call(ViewApplicationRequest(application), piClient).orThrow()
    assertEquals(ApplicationStatus.APPROVED, appAfter.application.status)

    val wallets = when (val recipient = appAfter.application.grantRecipient) {
        is GrantRecipient.PersonalProject -> {
            Wallets.retrieveBalance.call(
                RetrieveBalanceRequest(recipient.username, WalletOwnerType.USER),
                recipientClient
            ).orThrow()
        }
        is GrantRecipient.ExistingProject -> {
            Wallets.retrieveBalance.call(
                RetrieveBalanceRequest(recipient.projectId, WalletOwnerType.PROJECT, false),
                recipientClient
            ).orThrow()
        }
        is GrantRecipient.NewProject -> {
            val project = Projects.listProjects
                .call(ListProjectsRequest(itemsPerPage = 100), recipientClient)
                .orThrow().items.find { it.title == recipient.projectTitle }!!
                .projectId

            Wallets.retrieveBalance.call(
                RetrieveBalanceRequest(project, WalletOwnerType.PROJECT, false),
                recipientClient
            ).orThrow()
        }
    }.wallets

    for (request in appAfter.application.requestedResources) {
        val wallet = wallets.find {
            it.wallet.paysFor.id == request.productCategory && it.wallet.paysFor.provider == request.productProvider
        } ?: error("No relevant wallet found")

        assertThatInstance(wallet, "has enough credits") { it.balance >= request.creditsRequested!! }
    }
}

class GrantTest : IntegrationTest() {
    @Test
    fun `test simple application and core assumptions`() = t {
        val project = initializeNormalProject(initializeRootProject())
        enableGrants(project.projectId)

        val requestSettings = Grants.readRequestSettings.call(
            ReadRequestSettingsRequest(project.projectId),
            project.piClient
        ).orThrow()

        assertThatInstance(requestSettings, "automatic approval is empty by default") {
            it.automaticApproval.maxResources.isEmpty()
        }
        assertThatInstance(requestSettings, "allow requests from is empty by default") {
            it.allowRequestsFrom.isEmpty()
        }

        // We just want to check that we can read the templates. Contents doesn't really matter.
        Grants.readTemplates.call(
            ReadTemplatesRequest(project.projectId),
            project.piClient
        ).orThrow()

        val imada = createUser(username = "a", email = "a@imada.sdu.dk")

        allowGrantsFrom(project.projectId, project.piClient, UserCriteria.EmailDomain("imada.sdu.dk"))

        val id = submitGrantApplication(project.projectId, imada.username, imada.client)

        assertThatInstance(
            Grants.ingoingApplications.call(
                IngoingApplicationsRequest(),
                project.piClient.withProject(project.projectId)
            ).orThrow(),
            "should contain our application"
        ) { it.items.any { it.id == id } }

        approveGrantApplication(id, project.piClient, imada.client)

        assertThatInstance(
            Grants.ingoingApplications.call(
                IngoingApplicationsRequest(),
                project.piClient.withProject(project.projectId)
            ).orThrow(),
            "should not contain our application"
        ) { it.items.none { it.id == id } }
    }

    @Test
    fun `test grant application fail when not allowed`() = t {
        val project = initializeNormalProject(initializeRootProject())
        enableGrants(project.projectId)
        val user = createUser()
        try {
            submitGrantApplication(project.projectId, user.username, user.client)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.Forbidden, ex.httpStatusCode)
        }
    }

    @Test
    fun `test that anyone can apply when enabled`() = t {
        val project = initializeNormalProject(initializeRootProject())
        enableGrants(project.projectId)
        allowGrantsFrom(project.projectId, project.piClient, UserCriteria.Anyone())
        val user = createUser()
        submitGrantApplication(project.projectId, user.username, user.client)
    }

    @Test
    fun `test uploading templates`() = t {
        val project = initializeNormalProject(initializeRootProject())
        enableGrants(project.projectId)

        val personal = "personal"
        val existing = "existing"
        val new = "new"
        Grants.uploadTemplates.call(
            UploadTemplatesRequest(personal, new, existing),
            project.piClient.withProject(project.projectId)
        ).orThrow()

        val templates = Grants.readTemplates.call(
            ReadTemplatesRequest(project.projectId),
            project.piClient
        ).orThrow()

        assertEquals(personal, templates.personalProject)
        assertEquals(existing, templates.existingProject)
        assertEquals(new, templates.newProject)
    }

    @Test
    fun `test browsing a project`() = t {
        val project = initializeNormalProject(initializeRootProject())
        enableGrants(project.projectId)
        val user = createUser()

        assertThatInstance(
            Grants.browseProjects.call(BrowseProjectsRequest(), user.client).orThrow(),
            "has no projects"
        ) { it.items.isEmpty() }

        allowGrantsFrom(project.projectId, project.piClient, UserCriteria.Anyone())

        assertThatInstance(
            Grants.browseProjects.call(BrowseProjectsRequest(), user.client).orThrow(),
            "has a we can apply to"
        ) { it.items.single().projectId == project.projectId }
    }

    @Test
    fun `test editing an application is only doable by participants`() = t {
        val grantApprover = initializeRootProject()
        val childProject = initializeNormalProject(grantApprover)
        val adminUser = createUser()
        val outsideUser = createUser()
        addMemberToProject(childProject.projectId, childProject.piClient, adminUser.client, adminUser.username)
        Projects.changeUserRole.call(
            ChangeUserRoleRequest(childProject.projectId, adminUser.username, ProjectRole.ADMIN),
            childProject.piClient
        ).orThrow()

        allowGrantsFrom(grantApprover, serviceClient, UserCriteria.Anyone())

        val id = submitGrantApplication(
            grantApprover,
            childProject.piUsername,
            childProject.piClient,
            GrantRecipient.ExistingProject(childProject.projectId)
        )

        // PI and grant approver should be the only ones who can edit the document
        val newDoc = "a"
        Grants.editApplication.call(
            EditApplicationRequest(id, newDoc, listOf(ResourceRequest.fromProduct(sampleCompute, 1000.DKK))),
            serviceClient
        ).orThrow()
        assertEquals(
            newDoc,
            Grants.viewApplication.call(ViewApplicationRequest(id), childProject.piClient).orThrow()
                .application.document
        )

        val newDoc2 = "a"
        Grants.editApplication.call(
            EditApplicationRequest(id, newDoc2, listOf(ResourceRequest.fromProduct(sampleCompute, 1000.DKK))),
            childProject.piClient
        ).orThrow()
        assertEquals(
            newDoc2,
            Grants.viewApplication.call(ViewApplicationRequest(id), childProject.piClient).orThrow()
                .application.document
        )

        // Everyone else should fail
        assertThatInstance(
            Grants.editApplication.call(
                EditApplicationRequest(id, "bad", emptyList()),
                adminUser.client
            ),
            "should fail"
        ) { !it.statusCode.isSuccess() }

        assertThatInstance(
            Grants.editApplication.call(
                EditApplicationRequest(id, "bad", emptyList()),
                outsideUser.client
            ),
            "should fail"
        ) { !it.statusCode.isSuccess() }
    }
}
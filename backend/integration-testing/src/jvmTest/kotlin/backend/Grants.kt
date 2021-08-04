package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletBrowseRequest
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.grant.api.ApplicationWithComments
import dk.sdu.cloud.grant.api.ApproveApplicationRequest
import dk.sdu.cloud.grant.api.AutomaticApprovalSettings
import dk.sdu.cloud.grant.api.CloseApplicationRequest
import dk.sdu.cloud.grant.api.CommentOnApplicationRequest
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.grant.api.GrantRecipient
import dk.sdu.cloud.grant.api.Grants
import dk.sdu.cloud.grant.api.RejectApplicationRequest
import dk.sdu.cloud.grant.api.ResourceRequest
import dk.sdu.cloud.grant.api.SetEnabledStatusRequest
import dk.sdu.cloud.grant.api.SubmitApplicationRequest
import dk.sdu.cloud.grant.api.UploadRequestSettingsRequest
import dk.sdu.cloud.grant.api.UserCriteria
import dk.sdu.cloud.grant.api.ViewApplicationRequest
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.assertUserError
import dk.sdu.cloud.project.api.AcceptInviteRequest
import dk.sdu.cloud.project.api.ChangeUserRoleRequest
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.InviteRequest
import dk.sdu.cloud.project.api.LeaveProjectRequest
import dk.sdu.cloud.project.api.ListProjectsRequest
import dk.sdu.cloud.project.api.ListSubProjectsRequest
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.TransferPiRoleRequest
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.test.UCloudTestCaseBuilder
import io.ktor.http.*
import java.util.*

class GrantTest : IntegrationTest() {
    sealed class CommentPoster {
        object User : CommentPoster()
        object Pi : CommentPoster()
        class Admin(val idx: Int) : CommentPoster()
    }

    override fun defineTests() {
        testFilter = { t, s -> !s.contains("happy path") }
        run {
            class Comment(val poster: CommentPoster, val commentToPost: String)

            class In(
                val outcome: ApplicationStatus?,
                val grantRecipient: GrantRecipient,
                val resourcesRequested: List<ResourceRequest>,
                val allowList: List<UserCriteria> = listOf(UserCriteria.Anyone()),
                val excludeList: List<UserCriteria> = listOf(),
                val numberOfProjectAdmins: Int = 0,
                val comments: List<Comment> = emptyList(),
                val ifExistingDoCreate: Boolean = true,
                val username: String? = null,
                val userOrganization: String? = "ucloud.dk",
                val userEmail: String = "ucloud@ucloud.dk",
                val document: String = "This is my document",
                val changeStatusBy: CommentPoster = CommentPoster.Pi,
            )

            class Out(
                val grantApplication: ApplicationWithComments,
                val projectsOfUser: List<UserProjectSummary>,
                val walletsOfUser: List<Wallet>,
                val childProjectsOfGrantGiver: List<Project>,
                val walletsOfGrantGiver: List<Wallet>,
            )

            test<In, Out>("Grant applications, expected flow") {
                execute {
                    val grantPi = createUser("pi-${UUID.randomUUID()}")
                    val grantAdmins = (0 until input.numberOfProjectAdmins).map {
                        createUser("admin-${UUID.randomUUID()}")
                    }
                    val evilUser = createUser("evil-${UUID.randomUUID()}")
                    val normalUser = createUser(
                        username = input.username ?: "user-${testId}",
                        email = input.userEmail,
                        organization = input.userOrganization
                    )
                    val createdProject = initializeRootProject(grantPi.username)

                    if (grantAdmins.isNotEmpty()) {
                        Projects.invite.call(
                            InviteRequest(createdProject, grantAdmins.map { it.username }.toSet()),
                            grantPi.client
                        ).orThrow()

                        for (admin in grantAdmins) {
                            Projects.acceptInvite.call(AcceptInviteRequest(createdProject), admin.client).orThrow()
                            Projects.changeUserRole.call(
                                ChangeUserRoleRequest(createdProject, admin.username, ProjectRole.ADMIN),
                                grantPi.client
                            ).orThrow()
                        }
                    }

                    var actualRecipient = input.grantRecipient
                    if (input.grantRecipient is GrantRecipient.ExistingProject && input.ifExistingDoCreate) {
                        val created = Projects.create.call(
                            CreateProjectRequest(
                                "Existing child",
                                parent = createdProject,
                                principalInvestigator = normalUser.username
                            ),
                            grantPi.client
                        ).orThrow()
                        Projects.invite.call(InviteRequest(created.id, setOf(normalUser.username)), grantPi.client)
                            .orThrow()
                        Projects.acceptInvite.call(AcceptInviteRequest(created.id), normalUser.client).orThrow()
                        Projects.transferPiRole.call(
                            TransferPiRoleRequest(normalUser.username),
                            grantPi.client.withProject(created.id)
                        ).orThrow()
                        Projects.leaveProject.call(LeaveProjectRequest, grantPi.client.withProject(created.id))
                            .orThrow()
                        actualRecipient = GrantRecipient.ExistingProject(created.id)
                    }

                    Grants.setEnabledStatus.call(SetEnabledStatusRequest(createdProject, true), serviceClient).orThrow()
                    val settingsRequest = UploadRequestSettingsRequest(
                        AutomaticApprovalSettings(emptyList(), emptyList()),
                        input.allowList,
                        input.excludeList
                    )

                    Grants.uploadRequestSettings.call(settingsRequest, grantPi.client.withProject(createdProject))
                            .orThrow()

                    Grants.uploadRequestSettings.call(
                        settingsRequest.copy(allowRequestsFrom = listOf(UserCriteria.WayfOrganization("Evil Corp"))),
                        evilUser.client.withProject(createdProject)
                    ).assertUserError()

                    val applicationId = Grants.submitApplication.call(
                        SubmitApplicationRequest(
                            createdProject,
                            actualRecipient,
                            input.document,
                            input.resourcesRequested
                        ),
                        normalUser.client
                    ).orThrow().id

                    for (comment in input.comments) {
                        Grants.commentOnApplication.call(
                            CommentOnApplicationRequest(applicationId, comment.commentToPost),
                            when (comment.poster) {
                                is CommentPoster.Admin -> grantAdmins[comment.poster.idx].client
                                CommentPoster.Pi -> grantPi.client
                                CommentPoster.User -> normalUser.client
                            }
                        ).orThrow()
                    }

                    Grants.commentOnApplication.call(
                        CommentOnApplicationRequest(applicationId, "Should fail"),
                        evilUser.client
                    ).assertUserError()

                    val clientToChange = when (val change = input.changeStatusBy) {
                        is CommentPoster.Admin -> grantAdmins[change.idx].client
                        CommentPoster.Pi -> grantPi.client
                        CommentPoster.User -> normalUser.client
                    }

                    when (input.outcome) {
                        ApplicationStatus.APPROVED -> {
                            Grants.approveApplication.call(
                                ApproveApplicationRequest(applicationId),
                                clientToChange
                            ).orThrow()
                        }
                        ApplicationStatus.REJECTED -> {
                            Grants.rejectApplication.call(
                                RejectApplicationRequest(applicationId),
                                clientToChange
                            ).orThrow()
                        }
                        ApplicationStatus.CLOSED -> {
                            Grants.closeApplication.call(
                                CloseApplicationRequest(applicationId),
                                clientToChange
                            ).orThrow()
                        }
                        else -> {
                            // Do nothing
                        }
                    }

                    Grants.approveApplication.call(ApproveApplicationRequest(applicationId), evilUser.client)
                        .assertUserError()
                    Grants.rejectApplication.call(RejectApplicationRequest(applicationId), evilUser.client)
                        .assertUserError()
                    Grants.closeApplication.call(CloseApplicationRequest(applicationId), evilUser.client)
                        .assertUserError()

                    val outputApplication =
                        Grants.viewApplication.call(ViewApplicationRequest(applicationId), normalUser.client).orThrow()

                    Grants.viewApplication.call(ViewApplicationRequest(applicationId), evilUser.client)
                        .assertUserError()

                    val userProjects = Projects.listProjects.call(ListProjectsRequest(), normalUser.client)
                        .orThrow().items
                    val childProjects = Projects.listSubProjects.call(
                        ListSubProjectsRequest(),
                        grantPi.client.withProject(createdProject)
                    ).orThrow().items

                    val userWallets = when (input.grantRecipient) {
                        is GrantRecipient.ExistingProject, is GrantRecipient.NewProject -> {
                            val project = userProjects.singleOrNull()?.projectId
                            if (project == null) emptyList()
                            else Wallets.browse.call(
                                WalletBrowseRequest(),
                                normalUser.client.withProject(project)
                            ).orThrow().items
                        }
                        is GrantRecipient.PersonalProject -> {
                            Wallets.browse.call(WalletBrowseRequest(), normalUser.client).orThrow().items
                        }
                    }

                    val grantWallets = Wallets.browse.call(
                        WalletBrowseRequest(),
                        grantPi.client.withProject(createdProject)
                    ).orThrow().items

                    Out(
                        outputApplication,
                        userProjects,
                        userWallets,
                        childProjects,
                        grantWallets
                    )
                }

                fun UCloudTestCaseBuilder<In, Out>.checkSuccess() {
                    check {
                        val outputComments = output.grantApplication.comments
                        val inputComments = input.comments
                        assertThatInstance(outputComments, "has the correct size") { it.size == inputComments.size }
                        for ((inputComment, outputComment) in inputComments.zip(outputComments)) {
                            assertThatPropertyEquals(outputComment, { it.comment }, inputComment.commentToPost)
                            assertThatInstance(outputComment, "should match poster") {
                                it.postedBy.startsWith(when(inputComment.poster) {
                                    is CommentPoster.Admin -> "admin-"
                                    CommentPoster.User -> "user-"
                                    CommentPoster.Pi -> "pi-"
                                })
                            }
                        }
                    }

                    check {
                        assertThatPropertyEquals(
                            output.grantApplication,
                            { it.application.status },
                            input.outcome ?: ApplicationStatus.IN_PROGRESS,
                            "actual outcome should match expected outcome"
                        )

                        assertThatInstance(output.grantApplication, "should be changed by expected user") {
                            if (input.outcome == null || input.outcome == ApplicationStatus.IN_PROGRESS) {
                                true
                            } else {
                                it.application.statusChangedBy?.startsWith(when(input.changeStatusBy) {
                                    is CommentPoster.Admin -> "admin-"
                                    CommentPoster.Pi -> "pi-"
                                    CommentPoster.User -> "user-"
                                }) == true
                            }
                        }
                    }

                    check {
                        when (val recipient = input.grantRecipient) {
                            is GrantRecipient.ExistingProject -> {
                                assertThatInstance(output.projectsOfUser) {
                                    if (input.ifExistingDoCreate) {
                                        it.size == 1
                                    } else {
                                        it.isEmpty()
                                    }
                                }
                            }
                            is GrantRecipient.NewProject -> {
                                assertThatInstance(output.projectsOfUser) {
                                    if (input.outcome == ApplicationStatus.APPROVED) {
                                        it.size == 1 && it.single().title == recipient.projectTitle
                                    } else {
                                        it.isEmpty()
                                    }
                                }
                            }
                            is GrantRecipient.PersonalProject -> {
                                assertThatInstance(output.projectsOfUser) { it.isEmpty() }
                            }
                        }
                    }

                    check {
                        assertThatInstance(output.walletsOfUser, "should have the expected size") {
                            input.outcome != ApplicationStatus.APPROVED || it.size == input.resourcesRequested.size
                        }

                        if (input.outcome == ApplicationStatus.APPROVED) {
                            for (wallet in output.walletsOfUser) {
                                val resolvedRequest = input.resourcesRequested.find {
                                    it.productCategory == wallet.paysFor.name &&
                                            it.productProvider == wallet.paysFor.provider
                                } ?: throw AssertionError("Received wallet but no such request was made: $wallet")

                                val expectedBalance = resolvedRequest.creditsRequested ?: resolvedRequest.quotaRequested
                                assertThatPropertyEquals(
                                    wallet,
                                    { it.allocations.sumOf { it.balance } },
                                    expectedBalance
                                )
                            }
                        }
                    }

                    check {
                        when (val recipient = input.grantRecipient) {
                            is GrantRecipient.NewProject, is GrantRecipient.ExistingProject -> {
                                if (input.outcome == ApplicationStatus.APPROVED) {
                                    assertThatInstance(output.childProjectsOfGrantGiver) { it.size == 1 }
                                }
                            }

                            is GrantRecipient.PersonalProject -> {
                                assertThatInstance(output.childProjectsOfGrantGiver) { it.size == 0 }
                            }
                        }
                    }
                }

                ApplicationStatus.values().forEach { outcome ->
                    val username = "user-${UUID.randomUUID()}"
                    listOf(
                        GrantRecipient.NewProject("MyProject"),
                        GrantRecipient.ExistingProject("replaced"),
                        GrantRecipient.PersonalProject(username)
                    ).forEach { recipient ->
                        listOf(0, 1).forEach { numberOfComments ->
                            (0..2).forEach { numberOfAdmins ->
                                case(buildString {
                                    append("happy path (")
                                    append("outcome = ")
                                    append(outcome)
                                    append(", recipient = ")
                                    append(recipient)
                                    append(", numberOfAdmins = ")
                                    append(numberOfAdmins)
                                    append(", numberOfComments = ")
                                    append(numberOfComments)
                                    append(")")
                                }) {
                                    input(
                                        In(
                                            outcome,
                                            recipient,
                                            listOf(
                                                ResourceRequest(
                                                    sampleCompute.category.name,
                                                    sampleCompute.category.provider,
                                                    100.DKK
                                                )
                                            ),
                                            numberOfProjectAdmins = numberOfAdmins,
                                            comments = buildList<Comment> {
                                                addAll((0 until numberOfComments).map {
                                                    Comment(CommentPoster.Pi, "This is my comment $it")
                                                })
                                                addAll(
                                                    (0 until numberOfAdmins).flatMap { admin ->
                                                        (0 until numberOfComments).map {
                                                            Comment(
                                                                CommentPoster.Admin(admin),
                                                                "This is my comment $it"
                                                            )
                                                        }
                                                    }
                                                )

                                                addAll((0 until numberOfComments).map {
                                                    Comment(CommentPoster.User, "This is my comment $it")
                                                })
                                            },
                                            username = username,
                                            changeStatusBy = if (outcome != ApplicationStatus.CLOSED) {
                                                if (numberOfAdmins == 0) {
                                                    CommentPoster.Pi
                                                } else {
                                                    CommentPoster.Admin(numberOfAdmins - 1)
                                                }
                                            } else {
                                                CommentPoster.User
                                            }
                                        )
                                    )

                                    checkSuccess()
                                }
                            }
                        }
                    }
                }

                case("organization requirement") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    input(In(
                        ApplicationStatus.APPROVED,
                        GrantRecipient.PersonalProject(username),
                        listOf(ResourceRequest(sampleCompute.category.name, sampleCompute.category.provider, 100.DKK)),
                        username = username,
                        allowList = listOf(UserCriteria.WayfOrganization(organization)),
                        userOrganization = organization
                    ))

                    checkSuccess()
                }

                case("organization requirement not met") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    val otherOrganization = "sdu.dk"
                    input(In(
                        ApplicationStatus.APPROVED,
                        GrantRecipient.PersonalProject(username),
                        listOf(ResourceRequest(sampleCompute.category.name, sampleCompute.category.provider, 100.DKK)),
                        username = username,
                        allowList = listOf(UserCriteria.WayfOrganization(organization)),
                        userOrganization = otherOrganization
                    ))

                    expectStatusCode(HttpStatusCode.Forbidden)
                }

                case("email requirement") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    input(In(
                        ApplicationStatus.APPROVED,
                        GrantRecipient.PersonalProject(username),
                        listOf(ResourceRequest(sampleCompute.category.name, sampleCompute.category.provider, 100.DKK)),
                        username = username,
                        allowList = listOf(UserCriteria.EmailDomain(organization)),
                        userEmail = "user@$organization"
                    ))

                    checkSuccess()
                }

                case("email requirement not met") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    val otherOrganization = "sdu.dk"
                    input(In(
                        ApplicationStatus.APPROVED,
                        GrantRecipient.PersonalProject(username),
                        listOf(ResourceRequest(sampleCompute.category.name, sampleCompute.category.provider, 100.DKK)),
                        username = username,
                        allowList = listOf(UserCriteria.EmailDomain(organization)),
                        userEmail = "user@$otherOrganization"
                    ))

                    expectStatusCode(HttpStatusCode.Forbidden)
                }

                case("not excluded") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    val otherOrganization = "sdu.dk"
                    input(In(
                        ApplicationStatus.APPROVED,
                        GrantRecipient.PersonalProject(username),
                        listOf(ResourceRequest(sampleCompute.category.name, sampleCompute.category.provider, 100.DKK)),
                        username = username,
                        allowList = listOf(UserCriteria.WayfOrganization(organization)),
                        excludeList = listOf(UserCriteria.EmailDomain(otherOrganization)),
                        userOrganization = organization,
                        userEmail = "user@$organization"
                    ))

                    checkSuccess()
                }

                case("excluded") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    val otherOrganization = "sdu.dk"
                    input(In(
                        ApplicationStatus.APPROVED,
                        GrantRecipient.PersonalProject(username),
                        listOf(ResourceRequest(sampleCompute.category.name, sampleCompute.category.provider, 100.DKK)),
                        username = username,
                        allowList = listOf(UserCriteria.WayfOrganization(organization)),
                        excludeList = listOf(UserCriteria.EmailDomain("student.$organization")),
                        userOrganization = organization,
                        userEmail = "user@student.$organization"
                    ))

                    expectStatusCode(HttpStatusCode.Forbidden)
                }
            }
        }
    }

    /*
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

        // PI should be the only ones who can edit the document
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
 */
}

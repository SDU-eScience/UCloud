package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Wallet
import dk.sdu.cloud.accounting.api.WalletBrowseRequest
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withHttpBody
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.assertUserError
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.test.UCloudTestCaseBuilder
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrantTest : IntegrationTest() {
    sealed class CommentPoster {
        object User : CommentPoster()
        object Pi : CommentPoster()
        class Admin(val idx: Int) : CommentPoster()
    }

    override fun defineTests() {

        run {
            class In(
                val uploadSettings: ProjectApplicationSettings,
                val resourceRequests: List<ResourceRequest>
            )
            class Out(
                val applicationStatus: ApplicationStatus
            )

            test<In, Out>("Auto approval tests") {
                execute {
                    val pi = createUser("userPI")
                    val applier = createUser("userApply", email = "mojn@schulz.dk")
                    createSampleProducts()

                    val root = initializeRootProject(pi.username)

                    Grants.setEnabledStatus.call(
                        SetEnabledStatusRequest(
                            root,
                            true
                        ),
                        serviceClient
                    ).orThrow()

                    Grants.uploadRequestSettings.call(
                        input.uploadSettings,
                        pi.client.withProject(root)
                    ).orThrow()

                    val applicationId = Grants.submitApplication.call(
                        SubmitApplicationRequest(
                            root,
                            GrantRecipient.NewProject("Say hello to my little friend"),
                            "I would like resources",
                            input.resourceRequests
                        ),
                        applier.client
                    ).orThrow().id

                    val appStatus = Grants.viewApplication.call(
                        ViewApplicationRequest(applicationId),
                        applier.client
                    ).orThrow().application.status

                    Out(appStatus)
                }
                case("auto approve full fail check") {
                    input(
                        In(
                            uploadSettings = (
                                ProjectApplicationSettings(
                                    automaticApproval = AutomaticApprovalSettings(
                                        listOf(
                                            UserCriteria.EmailDomain("wrong.dk")
                                        ),
                                        listOf(
                                            ResourceRequest(
                                                sampleCompute.category.name,
                                                sampleCompute.category.provider,
                                                1000
                                            ),
                                            ResourceRequest(
                                                sampleStorage.category.name,
                                                sampleStorage.category.provider,
                                                500
                                            )
                                        )
                                    ),
                                    allowRequestsFrom = listOf(UserCriteria.Anyone()),
                                    excludeRequestsFrom = emptyList()
                                )
                            ),
                            resourceRequests = listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    1200
                                )
                            )
                        )
                    )
                    check {
                        assertEquals(ApplicationStatus.IN_PROGRESS, output.applicationStatus)
                    }
                }

                case("auto approve full accept check") {
                    input(
                        In(
                            uploadSettings = (
                                ProjectApplicationSettings(
                                    automaticApproval = AutomaticApprovalSettings(
                                        listOf(
                                            UserCriteria.EmailDomain("schulz.dk")
                                        ),
                                        listOf(
                                            ResourceRequest(
                                                sampleCompute.category.name,
                                                sampleCompute.category.provider,
                                                1000
                                            ),
                                            ResourceRequest(
                                                sampleStorage.category.name,
                                                sampleStorage.category.provider,
                                                500
                                            )
                                        )
                                    ),
                                    allowRequestsFrom = listOf(UserCriteria.Anyone()),
                                    excludeRequestsFrom = emptyList()
                                )
                                ),
                            resourceRequests = listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    800
                                )
                            )
                        )
                    )
                    check {
                        assertEquals(ApplicationStatus.APPROVED, output.applicationStatus)
                    }
                }

                case("auto approve partial fail check") {
                    input(
                        In(
                            uploadSettings = (
                                ProjectApplicationSettings(
                                    automaticApproval = AutomaticApprovalSettings(
                                        listOf(
                                            UserCriteria.EmailDomain("wrong.dk")
                                        ),
                                        listOf(
                                            ResourceRequest(
                                                sampleCompute.category.name,
                                                sampleCompute.category.provider,
                                                1000
                                            ),
                                            ResourceRequest(
                                                sampleStorage.category.name,
                                                sampleStorage.category.provider,
                                                500
                                            )
                                        )
                                    ),
                                    allowRequestsFrom = listOf(UserCriteria.Anyone()),
                                    excludeRequestsFrom = emptyList()
                                )
                                ),
                            resourceRequests = listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    800
                                )
                            )
                        )
                    )
                    check {
                        assertEquals(ApplicationStatus.IN_PROGRESS, output.applicationStatus)
                    }
                }
            }

        }

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
                val initialDocument: String = "This is my document",
                val document: String = initialDocument,
                val changeStatusBy: CommentPoster = CommentPoster.Pi,
            )

            class Out(
                val grantApplication: ApplicationWithComments,
                val projectsOfUser: List<UserProjectSummary>,
                val walletsOfUser: List<Wallet>,
                val childProjectsOfGrantGiver: List<MemberInProject>,
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
                    assertTrue(
                        Grants.isEnabled.call(IsEnabledRequest(createdProject), grantPi.client).orThrow().enabled
                    )

                    val initialSettings = Grants.readRequestSettings
                        .call(ReadRequestSettingsRequest(createdProject), grantPi.client).orThrow()

                    assertThatInstance(initialSettings, "has no default allow") {
                        it.allowRequestsFrom.isEmpty()
                    }
                    assertThatInstance(initialSettings, "has no default auto-approve") {
                        it.automaticApproval.from.isEmpty() && it.automaticApproval.maxResources.isEmpty()
                    }
                    assertThatInstance(initialSettings, "has no default exclude") {
                        it.excludeRequestsFrom.isEmpty()
                    }

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

                    val settingsAfterUpload = Grants.readRequestSettings
                        .call(ReadRequestSettingsRequest(createdProject), grantPi.client).orThrow()

                    assertThatInstance(settingsAfterUpload, "has the new allow list") {
                        it.allowRequestsFrom == input.allowList
                    }
                    assertThatInstance(settingsAfterUpload, "has no auto-approve") {
                        it.automaticApproval.from.isEmpty() && it.automaticApproval.maxResources.isEmpty()
                    }
                    assertThatInstance(settingsAfterUpload, "has the new exclude") {
                        it.excludeRequestsFrom == input.excludeList
                    }

                    val projectsToChoseFrom = Grants.browseProjects.call(
                        BrowseProjectsRequest(),
                        normalUser.client
                    ).orThrow()

                    // NOTE(Dan): Don't throw yet since submitApplication might not work
                    val productsToChoose = Grants.retrieveProducts.call(
                        GrantsRetrieveProductsRequest(
                            createdProject,
                            when (actualRecipient) {
                                is GrantRecipient.ExistingProject -> GrantRecipient.EXISTING_PROJECT_TYPE
                                is GrantRecipient.NewProject -> GrantRecipient.NEW_PROJECT_TYPE
                                is GrantRecipient.PersonalProject -> GrantRecipient.PERSONAL_TYPE
                            },
                            when (actualRecipient) {
                                is GrantRecipient.ExistingProject -> actualRecipient.projectId
                                is GrantRecipient.NewProject -> actualRecipient.projectTitle
                                is GrantRecipient.PersonalProject -> actualRecipient.username
                            }
                        ),
                        normalUser.client
                    )

                    val applicationId = Grants.submitApplication.call(
                        SubmitApplicationRequest(
                            createdProject,
                            actualRecipient,
                            input.initialDocument,
                            input.resourcesRequested
                        ),
                        normalUser.client
                    ).orThrow().id

                    // If we manage to submit the application then we must be able to see the project in
                    // `browseProjects`
                    assertThatInstance(projectsToChoseFrom, "has the project") { page ->
                        page.items.any { it.projectId == createdProject }
                    }

                    // Also check that the products were visible
                    val allProducts = productsToChoose.orThrow().availableProducts.groupBy { it.category }
                    for (request in input.resourcesRequested) {
                        assertThatInstance(allProducts, "has the product for $request") {
                            allProducts[ProductCategoryId(request.productCategory, request.productProvider)] != null
                        }
                    }

                    // Verify that the application is visible as an ingoing and as an outgoing application
                    assertThatInstance(
                        Grants.ingoingApplications.call(
                            IngoingApplicationsRequest(),
                            grantPi.client.withProject(createdProject)
                        ).orThrow().items,
                        "has the ingoing application"
                    ) {
                        it.size == 1 && it.single().requestedBy == normalUser.username &&
                                it.single().id == applicationId
                    }

                    assertThatInstance(
                        Grants.outgoingApplications.call(
                            OutgoingApplicationsRequest(),
                            normalUser.client.let {
                                val projectIdMaybe = (actualRecipient as? GrantRecipient.ExistingProject)?.projectId
                                if (projectIdMaybe != null) it.withProject(projectIdMaybe)
                                else it
                            }
                        ).orThrow().items,
                        "has the outgoing application"
                    ) {
                        it.size == 1 && it.single().requestedBy == normalUser.username &&
                                it.single().id == applicationId
                    }

                    assertThatInstance(
                        Grants.ingoingApplications.call(
                            IngoingApplicationsRequest(),
                            evilUser.client.withProject(createdProject)
                        ).orNull()?.items ?: emptyList(),
                        "is empty or fails"
                    ) { it.isEmpty() }

                    assertThatInstance(
                        Grants.ingoingApplications.call(
                            IngoingApplicationsRequest(),
                            evilUser.client
                        ).orThrow(),
                        "is empty"
                    ) { it.items.isEmpty() }

                    assertThatInstance(
                        Grants.outgoingApplications.call(
                            OutgoingApplicationsRequest(),
                            evilUser.client
                        ).orThrow(),
                        "is empty"
                    ) { it.items.isEmpty() }

                    // Create and delete a single comment (it shouldn't affect the output)
                    Grants.commentOnApplication.call(
                        CommentOnApplicationRequest(applicationId, "To be deleted!"),
                        grantPi.client
                    ).orThrow()

                    val commentId = Grants.viewApplication.call(ViewApplicationRequest(applicationId), grantPi.client)
                        .orThrow().comments.singleOrNull()?.id ?: error("found no comment")
                    Grants.deleteComment.call(DeleteCommentRequest(commentId), evilUser.client).assertUserError()
                    Grants.deleteComment.call(DeleteCommentRequest(commentId), grantPi.client).orThrow()

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

                    if (input.initialDocument != input.document) {
                        Grants.editApplication.call(
                            EditApplicationRequest(applicationId, input.document, emptyList()),
                            normalUser.client
                        ).orThrow()
                    }

                    Grants.editApplication.call(
                        EditApplicationRequest(
                            applicationId,
                            "Totally wrong document which should not be updated",
                            input.resourcesRequested
                        ),
                        grantPi.client
                    ).orThrow()

                    Grants.editApplication.call(
                        EditApplicationRequest(
                            applicationId,
                            "Evil document",
                            input.resourcesRequested.map { it.copy(balanceRequested = 1337.DKK) }
                        ),
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
                                it.postedBy.startsWith(
                                    when (inputComment.poster) {
                                        is CommentPoster.Admin -> "admin-"
                                        CommentPoster.User -> "user-"
                                        CommentPoster.Pi -> "pi-"
                                    }
                                )
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
                                it.application.statusChangedBy?.startsWith(
                                    when (input.changeStatusBy) {
                                        is CommentPoster.Admin -> "admin-"
                                        CommentPoster.Pi -> "pi-"
                                        CommentPoster.User -> "user-"
                                    }
                                ) == true
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

                                val expectedBalance = resolvedRequest.balanceRequested
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

                case("multiple comments and requirements") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    input(
                        In(
                            ApplicationStatus.APPROVED,
                            GrantRecipient.PersonalProject(username),
                            listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100.DKK
                                ),
                                ResourceRequest(
                                    sampleStorage.category.name,
                                    sampleStorage.category.provider,
                                    100.DKK
                                )
                            ),
                            comments = buildList<Comment> {
                                addAll((0 until 2).map {
                                    Comment(CommentPoster.Pi, "This is my comment $it")
                                })
                                addAll((0 until 2).map {
                                    Comment(CommentPoster.User, "This is my user comment $it")
                                })
                            },
                            username = username,
                            allowList = listOf(UserCriteria.WayfOrganization(organization)),
                            userOrganization = organization
                        )
                    )
                    check {
                        assertEquals(4, output.grantApplication.comments.size)
                        assertEquals(2, output.grantApplication.application.requestedResources.size)
                    }
                }

                case("organization requirement") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    input(
                        In(
                            ApplicationStatus.APPROVED,
                            GrantRecipient.PersonalProject(username),
                            listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100.DKK
                                )
                            ),
                            username = username,
                            allowList = listOf(UserCriteria.WayfOrganization(organization)),
                            userOrganization = organization
                        )
                    )

                    checkSuccess()
                }

                case("organization requirement not met") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    val otherOrganization = "sdu.dk"
                    input(
                        In(
                            ApplicationStatus.APPROVED,
                            GrantRecipient.PersonalProject(username),
                            listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100.DKK
                                )
                            ),
                            username = username,
                            allowList = listOf(UserCriteria.WayfOrganization(organization)),
                            userOrganization = otherOrganization
                        )
                    )

                    expectStatusCode(HttpStatusCode.Forbidden)
                }

                case("email requirement") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    input(
                        In(
                            ApplicationStatus.APPROVED,
                            GrantRecipient.PersonalProject(username),
                            listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100.DKK
                                )
                            ),
                            username = username,
                            allowList = listOf(UserCriteria.EmailDomain(organization)),
                            userEmail = "user@$organization"
                        )
                    )

                    checkSuccess()
                }

                case("email requirement not met") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    val otherOrganization = "sdu.dk"
                    input(
                        In(
                            ApplicationStatus.APPROVED,
                            GrantRecipient.PersonalProject(username),
                            listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100.DKK
                                )
                            ),
                            username = username,
                            allowList = listOf(UserCriteria.EmailDomain(organization)),
                            userEmail = "user@$otherOrganization"
                        )
                    )

                    expectStatusCode(HttpStatusCode.Forbidden)
                }

                case("not excluded") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    val otherOrganization = "sdu.dk"
                    input(
                        In(
                            ApplicationStatus.APPROVED,
                            GrantRecipient.PersonalProject(username),
                            listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100.DKK
                                )
                            ),
                            username = username,
                            allowList = listOf(UserCriteria.WayfOrganization(organization)),
                            excludeList = listOf(UserCriteria.EmailDomain(otherOrganization)),
                            userOrganization = organization,
                            userEmail = "user@$organization"
                        )
                    )

                    checkSuccess()
                }

                case("excluded") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    val otherOrganization = "sdu.dk"
                    input(
                        In(
                            ApplicationStatus.APPROVED,
                            GrantRecipient.PersonalProject(username),
                            listOf(
                                ResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100.DKK
                                )
                            ),
                            username = username,
                            allowList = listOf(UserCriteria.WayfOrganization(organization)),
                            excludeList = listOf(UserCriteria.EmailDomain("student.$organization")),
                            userOrganization = organization,
                            userEmail = "user@student.$organization"
                        )
                    )

                    expectStatusCode(HttpStatusCode.Forbidden)
                }
            }
        }

        run {
            class In(
                val description: String,
                val personalTemplate: String,
                val newTemplate: String,
                val existingTemplate: String,
                val useDefaultTemplate: Boolean = false

            )
            class Out(
                val personaltemplate: String,
                val newTemplate: String,
                val exisitingtemplate: String
            )

            test<In, Out>("Grant applications, metadata") {
                execute {
                    val grantPi = createUser("pi-${UUID.randomUUID()}")
                    val evilUser = createUser("evil-${UUID.randomUUID()}")
                    val createdProject = initializeRootProject(grantPi.username)
                    // NOTE(Dan): This is a valid 1x1 gif
                    val logo = Base64.getDecoder().decode("R0lGODdhAQABAIAAABpkzhpkziwAAAAAAQABAAACAkQBADs=")
                    // NOTE(Dan): The evil logo is red
                    val evilLogo = Base64.getDecoder().decode("R0lGODdhAQABAIABAM4aGgAAACwAAAAAAQABAAACAkQBADs=")

                    Grants.setEnabledStatus.call(SetEnabledStatusRequest(createdProject, true), serviceClient).orThrow()
                    Grants.fetchDescription.call(FetchDescriptionRequest(createdProject), grantPi.client).orThrow()
                    Grants.uploadDescription.call(
                        UploadDescriptionRequest(createdProject, input.description),
                        grantPi.client
                    ).orThrow()
                    Grants.uploadDescription.call(
                        UploadDescriptionRequest(createdProject, "Evil!"),
                        evilUser.client
                    ).assertUserError()
                    val description =
                        Grants.fetchDescription.call(FetchDescriptionRequest(createdProject), grantPi.client).orThrow()
                    assertEquals(input.description, description.description)

                    Grants.fetchLogo.call(FetchLogoRequest(createdProject), grantPi.client).assertUserError()
                    Grants.uploadLogo.call(
                        UploadLogoRequest(createdProject),
                        grantPi.client.withHttpBody(ContentType.Image.GIF, logo.size.toLong(), ByteReadChannel(logo))
                    ).orThrow()
                    Grants.uploadLogo.call(
                        UploadLogoRequest(createdProject),
                        evilUser.client.withHttpBody(
                            ContentType.Image.GIF,
                            evilLogo.size.toLong(),
                            ByteReadChannel(evilLogo)
                        )
                    ).assertUserError()
                    val fetchedLogoBytes = (Grants.fetchLogo.call(
                        FetchLogoRequest(createdProject),
                        grantPi.client
                    ).ctx as OutgoingHttpCall).response?.readBytes() ?: ByteArray(0)

                    assertEquals(base64Encode(logo), base64Encode(fetchedLogoBytes))

                    if (!input.useDefaultTemplate) {
                        Grants.uploadTemplates.call(
                            UploadTemplatesRequest(
                                input.personalTemplate,
                                input.newTemplate,
                                input.existingTemplate
                            ),
                            grantPi.client.withProject(createdProject)
                        ).orThrow()
                    }

                    Grants.uploadTemplates.call(
                        UploadTemplatesRequest("Evil 1", "Evil 2", "Evil 3"),
                        evilUser.client.withProject(createdProject)
                    ).assertUserError()

                    val fetchedTemplates = Grants.readTemplates.call(
                        ReadTemplatesRequest(createdProject),
                        grantPi.client
                    ).orThrow()

                    Out(fetchedTemplates.personalProject, fetchedTemplates.newProject, fetchedTemplates.existingProject)
                }

                case("Normal data - insert template") {
                    input(In("Some description", "some template", "another template", "more templates"))
                    check {
                        assertEquals(input.newTemplate, output.newTemplate)
                        assertEquals(input.existingTemplate, output.exisitingtemplate)
                        assertEquals(input.personalTemplate, output.personaltemplate)
                    }
                }

                case("Normal data - default templates") {
                    //Does not change the descriptions. Default message is found in Grant/main
                    val defaultMessage = "Please describe the reason for applying for resources"
                    input(In("Some description", "some template", "another template", "more templates", true))
                    check {
                        assertEquals(defaultMessage, output.personaltemplate)
                        assertEquals(defaultMessage, output.newTemplate)
                        assertEquals(defaultMessage, output.exisitingtemplate)
                    }
                }
            }
        }
    }
}

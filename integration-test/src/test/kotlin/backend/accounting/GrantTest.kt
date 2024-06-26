package dk.sdu.cloud.integration.backend.accounting

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.integration.*
import dk.sdu.cloud.integration.utils.*
import dk.sdu.cloud.integration.utils.assertThatInstance
import dk.sdu.cloud.integration.utils.assertThatPropertyEquals
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.Time
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.*

class GrantTest : IntegrationTest() {
    sealed class CommentPoster {
        object User : CommentPoster()
        object Pi : CommentPoster()
        class Admin(val idx: Int) : CommentPoster()
    }

    override fun defineTests() {

        run {
            class Comment(val poster: CommentPoster, val commentToPost: String)
            class SimpleResourceRequest(val category: String, val provider: String, val balance: Long)
            class In(
                val outcome: GrantApplication.State?,
                val grantRecipient: GrantApplication.Recipient,
                val resourcesRequested: List<SimpleResourceRequest>,
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
                val grantApplication: GrantApplication,
                val projectsOfUser: List<UserProjectSummary>,
                val walletsOfUser: List<WalletV2>,
                val childProjectsOfGrantGiver: List<MemberInProject>,
            )

            test<In, Out>("Grant applications, expected flow") {
                execute {
                    val provider = createSampleProducts()

                    val root = initializeRootProject(provider.projectId)

                    val grantAdmins = (0 until input.numberOfProjectAdmins).map {
                        createUser("admin-${UUID.randomUUID()}")
                    }
                    val normalUser = createUser(
                        username = input.username ?: "user-${UUID.randomUUID()}",
                        email = input.userEmail,
                        organization = input.userOrganization
                    )

                    val walletAllocations = AccountingV2.browseWallets.call(
                        AccountingV2.BrowseWallets.Request(),
                        adminClient.withProject(root.projectId)
                    ).orThrow()

                    val requestedResources = input.resourcesRequested.map { requested ->
                        val alloc = walletAllocations.items.find {
                            it.paysFor.name == requested.category
                        }
                        GrantApplication.AllocationRequest(
                            requested.category,
                            requested.provider,
                            alloc?.owner?.reference() ?: root.projectId,
                            requested.balance,
                            GrantApplication.Period(
                                Time.now(),
                                Time.now() + 1_000_000
                            )
                        )
                    }

                    if (grantAdmins.isNotEmpty()) {
                        Projects.invite.call(
                            InviteRequest(root.projectId, grantAdmins.map { it.username }.toSet()),
                            root.piClient
                        ).orThrow()

                        for (admin in grantAdmins) {
                            Projects.acceptInvite.call(AcceptInviteRequest(root.projectId), admin.client)
                                .orThrow()
                            Projects.changeUserRole.call(
                                ChangeUserRoleRequest(root.projectId, admin.username, ProjectRole.ADMIN),
                                root.piClient
                            ).orThrow()
                        }
                    }

                    var actualRecipient = input.grantRecipient
                    val createdProject = if (input.grantRecipient is GrantApplication.Recipient.ExistingProject && input.ifExistingDoCreate) {
                        val created = Projects.create.call(
                            CreateProjectRequest(
                                "Existing child-${UUID.randomUUID()}",
                                parent = root.projectId,
                                principalInvestigator = normalUser.username
                            ),
                            root.piClient
                        ).orThrow()

                        actualRecipient = GrantApplication.Recipient.ExistingProject(created.id)
                        NormalProjectInitialization(
                            normalUser.client,
                            normalUser.username,
                            created.id
                        )
                    } else null


                    val evilUser = createUser("evil-${UUID.randomUUID()}").let {
                        it.copy(
                            client = if (createdProject != null ) it.client.withProject(createdProject.projectId) else it.client
                        )
                    }

                    val baseSettings = GrantRequestSettings(
                        true,
                        "Example description",
                        emptyList(),
                        emptyList(),
                        Templates.PlainText("Example", "Example", "Example")
                    )
                    GrantsV2.updateRequestSettings.call(
                        baseSettings,
                        serviceClient.withProject(root.projectId)
                    ).orThrow()

                    GrantsV2.updateRequestSettings.call(
                        baseSettings.copy(
                            allowRequestsFrom = input.allowList,
                            excludeRequestsFrom = input.excludeList,
                        ),
                        root.piClient.withProject(root.projectId)
                    ).orThrow()

                    GrantsV2.updateRequestSettings.call(
                        baseSettings,
                        evilUser.client.withProject(root.projectId)
                    ).assertUserError()

                    val settingsAfterUpload = GrantsV2.retrieveRequestSettings.call(
                        Unit,
                        root.piClient.withProject(root.projectId)
                    ).orThrow()

                    assertThatInstance(settingsAfterUpload, "has the new allow list") {
                        it.allowRequestsFrom == input.allowList
                    }
                    assertThatInstance(settingsAfterUpload, "has the new exclude") {
                        it.excludeRequestsFrom == input.excludeList
                    }

                    // NOTE(Dan): Don't throw yet since submitApplication might not work
                    val productsToChoose = GrantsV2.retrieveGrantGivers.call(
                        when (actualRecipient) {
                            is GrantApplication.Recipient.ExistingProject -> {
                                GrantsV2.RetrieveGrantGivers.Request.ExistingProject(actualRecipient.id)
                            }

                            is GrantApplication.Recipient.NewProject -> {
                                GrantsV2.RetrieveGrantGivers.Request.NewProject(actualRecipient.title)
                            }

                            is GrantApplication.Recipient.PersonalWorkspace -> {
                                GrantsV2.RetrieveGrantGivers.Request.PersonalWorkspace()
                            }
                        },
                        normalUser.client
                    ).orThrow()
                    println(productsToChoose.grantGivers.size)
                    for (i in productsToChoose.grantGivers) {
                        println("products: " + i.categories)
                    }

                    val applicationId = GrantsV2.submitRevision.call(
                        GrantsV2.SubmitRevision.Request(
                            GrantApplication.Document(
                                actualRecipient,
                                requestedResources,
                                GrantApplication.Form.PlainText(input.initialDocument),
                                null,
                                "revision",
                                root.projectId
                            ),
                            "Initial"
                        ),
                        normalUser.client
                    ).orThrow().id

                    // Verify that the application is visible as an ingoing and as an outgoing application
                    assertThatInstance(
                        GrantsV2.browse.call(
                            GrantsV2.Browse.Request(
                                includeIngoingApplications = true
                            ),
                            root.piClient.withProject(root.projectId)
                        ).orThrow().items,
                        "has the ingoing application"
                    ) {
                        (it.size == 1) && (it.single().createdBy == normalUser.username) &&
                                (it.single().id == applicationId.toString())
                    }

                    assertThatInstance(
                        GrantsV2.browse.call(
                            GrantsV2.Browse.Request(
                                includeOutgoingApplications = true
                            ),
                            normalUser.client.let {
                                val projectIdMaybe =
                                    (actualRecipient as? GrantApplication.Recipient.ExistingProject)?.id
                                if (projectIdMaybe != null) it.withProject(projectIdMaybe)
                                else it
                            }
                        ).orThrow().items,
                        "has the outgoing application"
                    ) {
                        (it.size == 1) && (it.single().createdBy == normalUser.username) &&
                                (it.single().id == applicationId.toString())
                    }

                    assertThatInstance(
                        GrantsV2.browse.call(
                            GrantsV2.Browse.Request(),
                            if (createdProject != null) evilUser.client.withProject(createdProject.projectId) else evilUser.client
                        ).orNull()?.items ?: emptyList(),
                        "is empty or fails"
                    ) { it.isEmpty() }

                    assertThatInstance(
                        GrantsV2.browse.call(
                            GrantsV2.Browse.Request(),
                            evilUser.client
                        ).orThrow(),
                        "is empty"
                    ) { it.items.isEmpty() }

                    // Create and delete a single comment (it shouldn't affect the output)
                    val commentId = GrantsV2.postComment.call(
                        GrantsV2.PostComment.Request(applicationId, "To be deleted!"),
                        root.piClient
                    ).orThrow().id

                    GrantsV2.deleteComment.call(
                        GrantsV2.DeleteComment.Request(applicationId, commentId),
                        evilUser.client
                    ).assertUserError()

                    GrantsV2.deleteComment.call(
                        GrantsV2.DeleteComment.Request(applicationId, commentId),
                        root.piClient
                    ).orThrow()

                    for (comment in input.comments) {
                        GrantsV2.postComment.call(
                            GrantsV2.PostComment.Request(applicationId, comment.commentToPost),
                            when (comment.poster) {
                                is CommentPoster.Admin -> grantAdmins[comment.poster.idx].client
                                CommentPoster.Pi -> createdProject?.piClient ?: normalUser.client
                                CommentPoster.User -> normalUser.client
                            }
                        ).orThrow()
                    }

                    GrantsV2.postComment.call(
                        GrantsV2.PostComment.Request(applicationId.toString(), "Should fail"),
                        evilUser.client
                    ).assertUserError()

                    if (input.initialDocument != input.document) {
                        GrantsV2.submitRevision.call(
                            GrantsV2.SubmitRevision.Request(
                                GrantApplication.Document(
                                    actualRecipient,
                                    emptyList(),
                                    GrantApplication.Form.PlainText(input.document),
                                ),
                                "comment",
                                applicationId,
                            ),
                            normalUser.client
                        ).orThrow()
                    }

                    GrantsV2.submitRevision.call(
                        GrantsV2.SubmitRevision.Request(
                            GrantApplication.Document(
                                actualRecipient,
                                requestedResources,
                                GrantApplication.Form.PlainText("Totally wrong document which should be updated"),
                                parentProjectId = root.projectId
                            ),
                            "comment",
                            applicationId,
                        ),
                        normalUser.client
                    ).orThrow()

                    GrantsV2.submitRevision.call(
                        GrantsV2.SubmitRevision.Request(
                            GrantApplication.Document(
                                actualRecipient,
                                requestedResources.map { it.copy(balanceRequested = 1337) },
                                GrantApplication.Form.PlainText("Evil document"),
                                parentProjectId = root.projectId
                            ),
                            "comment",
                            applicationId,
                        ),
                        evilUser.client
                    ).assertUserError()

                    //setting source allocations
                    val withAllocations = requestedResources.map { request ->
                        request
                    }

                    GrantsV2.submitRevision.call(
                        GrantsV2.SubmitRevision.Request(
                            GrantApplication.Document(
                                actualRecipient,
                                withAllocations,
                                GrantApplication.Form.PlainText("Totally wrong document which should be updated"),
                                parentProjectId = root.projectId
                            ),
                            "comment",
                            applicationId,
                        ),
                        createdProject?.piClient?.withProject(createdProject.projectId) ?: normalUser.client
                    )

                    val clientToChange = when (val change = input.changeStatusBy) {
                        is CommentPoster.Admin -> grantAdmins[change.idx].client.withProject(root.projectId)
                        CommentPoster.Pi -> root.piClient.withProject(root.projectId)
                        CommentPoster.User -> normalUser.client
                    }

                    GrantsV2.updateState.call(
                        GrantsV2.UpdateState.Request(
                            applicationId,
                            GrantApplication.State.APPROVED,
                        ),
                        evilUser.client,
                    ).assertUserError()

                    if (input.outcome != null) {
                        GrantsV2.updateState.call(
                            GrantsV2.UpdateState.Request(
                                applicationId,
                                input.outcome,
                            ),
                            clientToChange
                        ).orThrow()
                    }

                    val outputApplication = GrantsV2.retrieve.call(
                        FindByStringId(applicationId),
                        normalUser.client
                    ).orThrow()

                    GrantsV2.retrieve.call(
                        FindByStringId(applicationId),
                        evilUser.client
                    ).assertUserError()

                    val userProjects = Projects.listProjects.call(ListProjectsRequest(), normalUser.client)
                        .orThrow().items

                    val childProjects = Projects.listSubProjects.call(
                        ListSubProjectsRequest(),
                        root.piClient.withProject(root.projectId)
                    ).orThrow().items

                    val userWallets = when (input.grantRecipient) {
                        is GrantApplication.Recipient.ExistingProject, is GrantApplication.Recipient.NewProject -> {
                            val project = userProjects.singleOrNull()?.projectId
                            if (project == null) {
                                emptyList()
                            } else {

                                AccountingV2.browseWalletsInternal.call(
                                    AccountingV2.BrowseWalletsInternal.Request(
                                        WalletOwner.Project(project)
                                    ),
                                    adminClient.withProject(project)
                                ).orThrow().wallets
                            }
                        }

                        is GrantApplication.Recipient.PersonalWorkspace -> {
                            AccountingV2.browseWalletsInternal.call(
                                AccountingV2.BrowseWalletsInternal.Request(
                                    WalletOwner.User(normalUser.username),
                                ),
                                adminClient
                            ).orThrow().wallets
                        }
                    }

                    GrantsV2.updateRequestSettings.call(
                        baseSettings,
                        serviceClient.withProject(root.projectId)
                    ).orThrow()

                    Out(
                        outputApplication,
                        userProjects,
                        userWallets,
                        childProjects
                    )
                }

                fun UCloudTestCaseBuilder<In, Out>.checkSuccess() {
                    check {
                        val outputComments = output.grantApplication.status.comments
                        val inputComments = input.comments
                        assertThatInstance(outputComments, "has the correct size") { it.size == inputComments.size }
                        for ((inputComment, outputComment) in inputComments.zip(outputComments)) {
                            assertThatPropertyEquals(outputComment, { it.comment }, inputComment.commentToPost)
                            assertThatInstance(outputComment, "should match poster") {
                                it.username.startsWith(
                                    when (inputComment.poster) {
                                        is CommentPoster.Admin -> "admin-"
                                        CommentPoster.User -> "user-"
                                        CommentPoster.Pi -> "user-"
                                    }
                                )
                            }
                        }
                    }

                    check {
                        assertThatPropertyEquals(
                            output.grantApplication,
                            { it.status.overallState },
                            input.outcome ?: GrantApplication.State.IN_PROGRESS,
                            "actual outcome should match expected outcome"
                        )
                    }

                    check {
                        when (val recipient = input.grantRecipient) {
                            is GrantApplication.Recipient.ExistingProject -> {
                                assertThatInstance(output.projectsOfUser) {
                                    if (input.ifExistingDoCreate) {
                                        it.size == 1
                                    } else {
                                        it.isEmpty()
                                    }
                                }
                            }

                            is GrantApplication.Recipient.NewProject -> {
                                assertThatInstance(output.projectsOfUser) {
                                    if (input.outcome == GrantApplication.State.APPROVED) {
                                        it.size == 1 && it.single().title == recipient.title
                                    } else {
                                        it.isEmpty()
                                    }
                                }
                            }

                            is GrantApplication.Recipient.PersonalWorkspace -> {
                                assertThatInstance(output.projectsOfUser) { it.isEmpty() }
                            }
                        }
                    }

                    check {
                        assertThatInstance(output.walletsOfUser, "should have the expected size") {
                            input.outcome != GrantApplication.State.APPROVED || it.size == input.resourcesRequested.size
                        }

                        if (input.outcome == GrantApplication.State.APPROVED) {
                            for (wallet in output.walletsOfUser) {
                                val resolvedRequest = input.resourcesRequested.find {
                                    it.category == wallet.paysFor.name &&
                                            it.provider == wallet.paysFor.provider
                                } ?: throw AssertionError("Received wallet but no such request was made: $wallet")

                                val expectedBalance = resolvedRequest.balance
                                assertThatPropertyEquals(
                                    wallet,
                                    { it.quota },
                                    expectedBalance
                                )
                            }
                        }
                    }

                    check {
                        when (input.grantRecipient) {
                            is GrantApplication.Recipient.NewProject, is GrantApplication.Recipient.ExistingProject -> {
                                if (input.outcome == GrantApplication.State.APPROVED) {
                                    assertThatInstance(output.childProjectsOfGrantGiver) { it.size == 1 }
                                }
                            }

                            is GrantApplication.Recipient.PersonalWorkspace -> {
                                assertThatInstance(output.childProjectsOfGrantGiver) { it.isEmpty() }
                            }
                        }
                    }
                }

                GrantApplication.State.values().forEach { outcome ->
                    var username = "user-${UUID.randomUUID()}"
                    listOf(
                        GrantApplication.Recipient.NewProject("MyProject-${UUID.randomUUID()}"),
                        GrantApplication.Recipient.ExistingProject("replaced-${UUID.randomUUID()}"),
                        GrantApplication.Recipient.PersonalWorkspace(username)
                    ).forEach { recipient ->
                        listOf(0, 1).forEach { numberOfComments ->
                            (0..2).forEach { numberOfAdmins ->
                                val realrecept = when (recipient) {
                                    is GrantApplication.Recipient.PersonalWorkspace -> {
                                        //has to create a new username for personal workspaces to avoid conflicts
                                        username = "user-${UUID.randomUUID()}"
                                        recipient.copy(username)
                                    }

                                    is GrantApplication.Recipient.NewProject -> {
                                        //has to create a new projectname for new projects to avoid conflicts
                                        recipient.copy("MyProject-${UUID.randomUUID()}")
                                    }

                                    else -> recipient
                                }

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
                                            realrecept,
                                            listOf(
                                                SimpleResourceRequest(
                                                    sampleCompute.category.name,
                                                    sampleCompute.category.provider,
                                                    100
                                                )
                                            ),
                                            username = if (recipient is GrantApplication.Recipient.PersonalWorkspace) username else null,
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
                                            changeStatusBy = if (outcome != GrantApplication.State.CLOSED) {
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
                            GrantApplication.State.APPROVED,
                            GrantApplication.Recipient.PersonalWorkspace(username),
                            listOf(
                                SimpleResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100
                                ),
                                SimpleResourceRequest(
                                    sampleStorage.category.name,
                                    sampleStorage.category.provider,
                                    100
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
                        assertEquals(4, output.grantApplication.status.comments.size)
                        assertEquals(2, output.grantApplication.currentRevision.document.allocationRequests.size)
                    }
                }

                case("organization requirement") {
                    val username = "user-${UUID.randomUUID()}"
                    val organization = "ucloud.dk"
                    input(
                        In(
                            GrantApplication.State.APPROVED,
                            GrantApplication.Recipient.PersonalWorkspace(username),
                            listOf(
                                SimpleResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100
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
                            GrantApplication.State.APPROVED,
                            GrantApplication.Recipient.PersonalWorkspace(username),
                            listOf(
                                SimpleResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100
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
                            GrantApplication.State.APPROVED,
                            GrantApplication.Recipient.PersonalWorkspace(username),
                            listOf(
                                SimpleResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100
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
                            GrantApplication.State.APPROVED,
                            GrantApplication.Recipient.PersonalWorkspace(username),
                            listOf(
                                SimpleResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100
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
                            GrantApplication.State.APPROVED,
                            GrantApplication.Recipient.PersonalWorkspace(username),
                            listOf(
                                SimpleResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100
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
                    input(
                        In(
                            GrantApplication.State.APPROVED,
                            GrantApplication.Recipient.PersonalWorkspace(username),
                            listOf(
                                SimpleResourceRequest(
                                    sampleCompute.category.name,
                                    sampleCompute.category.provider,
                                    100
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
            class In
            class Out

            test<In, Out>("Grant applications logo") {
                execute {

                    val info  = createSampleProducts()

                    val root = initializeRootProject(info.projectId)
                    val createdProject = initializeNormalProject(root)
                    val evilUser = createUser("evil-${UUID.randomUUID()}").let {
                        it.copy(
                            client = it.client.withProject(createdProject.projectId)
                        )
                    }
                    // NOTE(Dan): This is a valid 1x1 gif
                    val logo = Base64.getDecoder().decode("R0lGODdhAQABAIAAABpkzhpkziwAAAAAAQABAAACAkQBADs=")
                    // NOTE(Dan): The evil logo is red
                    val evilLogo = Base64.getDecoder().decode("R0lGODdhAQABAIABAM4aGgAAACwAAAAAAQABAAACAkQBADs=")

                    GrantsV2.uploadLogo.call(
                        Unit,
                        createdProject.piClient.withHttpBody(
                            ContentType.Image.GIF,
                            logo.size.toLong(),
                            ByteReadChannel(logo)
                        )
                    ).orThrow()

                    GrantsV2.uploadLogo.call(
                        Unit,
                        evilUser.client.withHttpBody(
                            ContentType.Image.GIF,
                            evilLogo.size.toLong(),
                            ByteReadChannel(evilLogo)
                        )
                    ).assertUserError()

                    val fetchedLogoBytes =
                        GrantsV2.retrieveLogo
                            .call(
                                GrantsV2.RetrieveLogo.Request(createdProject.projectId),
                                createdProject.piClient
                            )
                            .let { it.ctx as OutgoingHttpCall }
                            .response
                            ?.readBytes() ?: ByteArray(0)

                    assertEquals(base64Encode(logo), base64Encode(fetchedLogoBytes))

                    Out()
                }

                case("Base case") {
                    input(In())
                    expectSuccess()
                }
            }
        }
    }
}

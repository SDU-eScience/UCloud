package dk.sdu.cloud.grant.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

val Int.DKK: Long get() = toLong() * 1_000_000
fun Long.creditsToDKK(): Long = this / 1_000_000


@Serializable
@UCloudApiStable
data class UpdateApplicationState(val applicationId: Long, val newState: GrantApplication.State, val notify: Boolean = true)
typealias UpdateApplicationStateResponse = Unit


@Serializable
@UCloudApiStable
data class CloseApplicationRequest(val applicationId: String)
typealias CloseApplicationResponse = Unit

@Serializable
@UCloudApiStable
data class TransferApplicationRequest(
    val applicationId: Long,
    val transferToProjectId: String,
    val revisionComment: String
)
typealias TransferApplicationResponse = Unit

@Serializable
@UCloudApiStable
enum class GrantApplicationFilter {
    SHOW_ALL,
    ACTIVE,
    INACTIVE
}

interface BrowseApplicationFlags {
    val includeIngoingApplications: Boolean
    val includeOutgoingApplications: Boolean
}

@Serializable
@UCloudApiStable
data class BrowseApplicationsRequest(
    val filter: GrantApplicationFilter = GrantApplicationFilter.ACTIVE,

    override val includeIngoingApplications: Boolean = false,
    override val includeOutgoingApplications: Boolean = false,

    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2, BrowseApplicationFlags
typealias BrowseApplicationsResponse = PageV2<GrantApplication>

typealias SubmitApplicationRequest = CreateApplication
typealias SubmitApplicationResponse = BulkResponse<FindByLongId>

@Serializable
@UCloudApiStable
data class EditApplicationRequest(
    val applicationId: Long,
    val document: GrantApplication.Document
)
typealias EditApplicationResponse = Unit

@Serializable
@UCloudApiStable
data class CreateApplication(
    val document: GrantApplication.Document
) {
    init {
        if (document.allocationRequests.isEmpty()) {
            throw RPCException("You must request at least one resource", HttpStatusCode.BadRequest)
        }
    }
}

@Serializable
@UCloudApiStable
data class GrantApplication(
    @UCloudApiDoc("""
        A unique identifier representing a GrantApplication
    
        The ID is used for all requests which manipulate the application. The ID is issued by UCloud/Core when the
        initial revision is submitted. The ID is never re-used by the system, even if a previous version has been
        closed.
    """)
    val id: String,

    @UCloudApiDoc("Username of the user who originially submitted the application")
    val createdBy: String,
    @UCloudApiDoc("Timestamp representing when the application was originially submitted")
    val createdAt: Long,
    @UCloudApiDoc("Timestamp representing when the application was last updated")
    val updatedAt: Long,

    @UCloudApiDoc("Information about the current revision")
    val currentRevision: Revision,

    @UCloudApiDoc("Status information about the application in its entireity")
    val status: Status,
) {
    @UCloudApiDoc(
        """
            Contains information about a specific revision of the application.
        
            The primary contents of the revision is stored in the document. The document describes the contents of the
            application, including which resource allocations are requested and by whom. Every time a change is made to
            the application, a new revision is created. Each revision contains the full document. Changes between versions
            can be computed by comparing with the previous revision.
        """
    )

    @Serializable
    @SerialName("revision")
    @UCloudApiStable
    data class Revision(
        @UCloudApiDoc("Timestamp indicating when this revision was made")
        val createdAt: Long,

        @UCloudApiDoc("Username of the user who created this revision")
        val updatedBy: String,

        @UCloudApiDoc(
            """
                A number indicating which revision this is

                Revision numbers are guaranteed to be unique and always increasing. The first revision number must be 0.
                The backend does not guarantee that revision numbers are issued without gaps. Thus it is allowed for the
                first revision to be 0 and the second revision to be 10.
            """
        )
        val revisionNumber: Int,

        @UCloudApiDoc("Contains the application form from the end-user")
        val document: Document
    )

    @Serializable
    @SerialName("document")
    @UCloudApiStable
    data class Document(
        @UCloudApiDoc(
            """
                Describes the recipient of resources, should the application be accepted

                Updateable by: Original creator (createdBy of application)
                Immutable after creation: Yes
            """
        )
        val recipient: Recipient,

        @UCloudApiDoc(
            """
                Describes the allocations for resources which are requested by this application

                Updateable by: Original creator and grant givers
                Immutable after creation: No
            """
        )
        val allocationRequests: List<AllocationRequest>,

        @UCloudApiDoc(
            """
                A form describing why these resources are being requested

                Updateable by: Original creator
                Immutable after creation: No
            """
        )
        val form: Form,

        @UCloudApiDoc(
            """
                A reference used for out-of-band book-keeping

                Updateable by: Grant givers
                Immutable after creation: No
            """
        )
        val referenceId: String? = null,

        @UCloudApiDoc(
            """
                A comment describing why this change was made

                Update by: Original creator and grant givers
                Immutable after creation: No. First revision must always be null.
            """
        )
        val revisionComment: String? = null,

        @UCloudApiDoc(
            """
                When creating a new project the user should choose one of the affiliations to be its parent.
            """
        )
        val parentProjectId: String? = null
    )

    @Serializable
    @SerialName("form")
    @UCloudApiStable
    sealed class Form {
        @Serializable
        @SerialName("plain_text")
        data class PlainText(val text: String): Form()
    }

    @Serializable
    @SerialName("recipient")
    @UCloudApiStable
    sealed class Recipient {
        @Serializable
        @SerialName("existingProject")
        @UCloudApiStable
        data class ExistingProject(val id: String) : Recipient()

        @Serializable
        @SerialName("newProject")
        @UCloudApiStable
        data class NewProject(val title: String) : Recipient()

        @Serializable
        @SerialName("personalWorkspace")
        @UCloudApiStable
        data class PersonalWorkspace(val username: String) : Recipient()

        companion object {
            const val PERSONAL_TYPE = "personal"
            const val EXISTING_PROJECT_TYPE = "existing_project"
            const val NEW_PROJECT_TYPE = "new_project"
        }
    }

    @Serializable
    @SerialName("allocation_request")
    @UCloudApiOwnedBy(Grants::class)
    @UCloudApiStable
    data class AllocationRequest(
        val category: String,
        val provider: String,
        val grantGiver: String,
        val balanceRequested: Long? = null,
        val sourceAllocation: Long? = null,
        val period: Period,
    )

    @Serializable
    @SerialName("period")
    @UCloudApiStable
    data class Period(
        val start: Long?,
        val end: Long?,
    )

    @Serializable
    @SerialName("status")
    @UCloudApiStable
    data class Status(
        val overallState: State,
        val stateBreakdown: List<GrantGiverApprovalState>,
        val comments: List<Comment>,
        val revisions: List<Revision>,
        val projectTitle: String?,
        val projectPI: String
    )

    @Serializable
    @SerialName("grant_giver_approval_state")
    @UCloudApiStable
    data class GrantGiverApprovalState(
        val projectId: String,
        val projectTitle: String,
        val state: State,
    )

    @Serializable
    @SerialName("state")
    @UCloudApiStable
    enum class State {
        APPROVED,
        REJECTED,
        CLOSED,
        IN_PROGRESS
    }

    @Serializable
    @SerialName("comment")
    @UCloudApiStable
    data class Comment(
        val id: String,
        val username: String,
        val createdAt: Long,
        val comment: String,
    )
}

@Serializable
@UCloudApiStable
data class RetrieveApplicationRequest(val id: Long)
typealias RetrieveApplicationResponse = GrantApplication

@Serializable
@UCloudApiStable
data class BrowseProjectsRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

typealias BrowseProjectsResponse = PageV2<ProjectWithTitle>

@Serializable
@UCloudApiStable
data class ProjectWithTitle(val projectId: String, val title: String)

@Serializable
@UCloudApiStable
data class GrantsBrowseAffiliationsRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    val recipientId: String? = null,
    val recipientType: String? = null,
) : WithPaginationRequestV2
typealias GrantsBrowseAffiliationsResponse = PageV2<ProjectWithTitle>

@Serializable
@UCloudApiStable
data class GrantsBrowseAffiliationsByResourceRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    val applicationId: String
) : WithPaginationRequestV2
typealias GrantsBrowseAffiliationsByResourceResponse = PageV2<ProjectWithTitle>

@Serializable
@UCloudApiStable
data class GrantsBrowseProductsRequest(
    val projectId: String,
    val recipientType: String,
    val recipientId: String,
    val showHidden: Boolean = true
)

@Serializable
@UCloudApiStable
data class GrantsBrowseProductsResponse(
    val availableProducts: List<Product>
)

@UCloudApiStable
object Grants : CallDescriptionContainer("grant") {
    val baseContext = "/api/grant"

    init {
        title = "Grant Applications"
        description = """
Grants provide a way for users of UCloud to apply for resources.

In order for any user to use UCloud they must have credits. Credits are required for use of any compute or 
storage. There are only two ways of receiving any credits, either through an admin directly granting you the 
credits or by receiving them from a project.

Grants acts as a more user-friendly gateway to receiving resources from a project. Every
`GrantApplication` goes through the following steps:

1. User submits application to relevant project using `Grants.submitApplication`
2. All grant approvers must review the application
   - User and reviewer can comment on the application via `GrantComments.createComment`
   - User and reviewer can perform edits to the application via `Grants.editApplication`
3. Reviewer either performs `Grants.updateApplicationState` to approve or reject
4. If the `GrantApplication` was approved then resources are granted to the `GrantApplication.recipient`
        """.trimIndent()
    }

    val submitApplication =
        call(
            "submitApplication",
            BulkRequest.serializer(SubmitApplicationRequest.serializer()),
            BulkResponse.serializer(FindByLongId.serializer()),
            CommonErrorMessage.serializer()
        ) {
            httpCreate(
                baseContext,
                "submit-application"
            )

            documentation {
                summary = "Submits a $TYPE_REF GrantApplication to a project"
                description = """
                     In order for the user to submit an application they must match any criteria in
                     `ProjectApplicationSettings.allowRequestsFrom`. If they are not the request will fail.
                """.trimIndent()
            }
        }


    val updateApplicationState = call("updateApplicationState", BulkRequest.serializer(UpdateApplicationState.serializer()), UpdateApplicationStateResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(
            baseContext,
            "update-state"
        )

       documentation {
            summary = "Approves or rejects an existing $TYPE_REF GrantApplication. If accepted by all grant givers this will " +
                "trigger granting of resources to the `GrantApplication.Document.recipient`. "
            description = "Only the grant reviewer can perform this action."
        }
    }


    val editApplication = call(
        "editApplication",

        BulkRequest.serializer(EditApplicationRequest.serializer()),
        EditApplicationResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(
            baseContext,
            "edit"
        )

        documentation {
            summary = "Performs an edit to an existing $TYPE_REF GrantApplication"
            description = "Both the creator and any of the grant reviewers are allowed to edit the application."
        }
    }

    val closeApplication = call(
        "closeApplication",
        BulkRequest.serializer(CloseApplicationRequest.serializer()),
        CloseApplicationResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(
            baseContext,
            "close"
        )

        documentation {
            summary = "Closes an existing $TYPE_REF GrantApplication"
            description = "This action is identical to rejecting the $TYPE_REF GrantApplication using " +
                "`updateApplicationState` except it can be performed by the $TYPE_REF GrantApplication creator."
        }
    }

    val transferApplication =
        call(
            "transferApplication",
            BulkRequest.serializer(TransferApplicationRequest.serializer()),
            TransferApplicationResponse.serializer(),
            CommonErrorMessage.serializer()
        ) {
            httpUpdate(
                baseContext,
                "transfer",
                Roles.AUTHENTICATED
            )

            documentation {
                summary = "Transfers allocation request to other root project"
            }
        }

    val browseApplications =
        call(
            "browseApplications",
            BrowseApplicationsRequest.serializer(),
            BrowseApplicationsResponse.serializer(GrantApplication.serializer()),
            CommonErrorMessage.serializer()
        ) {
            httpBrowse(
                baseContext
            )

            documentation {
                summary = "List active $TYPE_REF GrantApplication s"
                description = """Lists active `GrantApplication`s which are relevant to a project. By using
                    $TYPE_REF BrowseApplicationFlags it is possible to filter on ingoing and/or outgoing.
                """.trimIndent()
            }
        }

    val browseProjects = call(
        "browseProjects",
        BrowseProjectsRequest.serializer(),
        BrowseProjectsResponse.serializer(ProjectWithTitle.serializer()),
        CommonErrorMessage.serializer()
    ) {
        httpBrowse(
            baseContext,
            "projects"
        )

        documentation {
            summary = "Endpoint for users to browse projects which they can send a $TYPE_REF GrantApplication to"
            description = """
                 Concretely, this will return a list for which the user matches the criteria listed in
                 `ProjectApplicationSettings.allowRequestsFrom`.
            """.trimIndent()
        }
    }

    val browseAffiliationsByResource = call(
        "browseAffiliationsByResource",
        GrantsBrowseAffiliationsByResourceRequest.serializer(),
        GrantsBrowseAffiliationsByResourceResponse.serializer(ProjectWithTitle.serializer()),
        CommonErrorMessage.serializer()
    ) {
        httpBrowse(
            baseContext,
            "affiliationsByResource"
        )
    }

    val browseAffiliations = call(
        "retrieveAffiliations",
        GrantsBrowseAffiliationsRequest.serializer(),
        GrantsBrowseAffiliationsResponse.serializer(ProjectWithTitle.serializer()),
        CommonErrorMessage.serializer()
    ) {
        httpBrowse(
            baseContext,
            "affiliations"
        )
    }

    val browseProducts = call(
        "browseProducts",
        GrantsBrowseProductsRequest.serializer(),
        GrantsBrowseProductsResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpBrowse(
            baseContext,
            "products"
        )
    }

    val retrieveApplication = call(
        "viewApplication",
        RetrieveApplicationRequest.serializer(),
        RetrieveApplicationResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpRetrieve(
            baseContext
        )

        documentation {
            summary = "Retrieves an active $TYPE_REF GrantApplication"
            description = """
                Only the creator and grant reviewers are allowed to view any given $TYPE_REF GrantApplication.
            """.trimIndent()
        }
    }
}

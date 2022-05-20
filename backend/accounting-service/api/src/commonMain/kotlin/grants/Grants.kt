package dk.sdu.cloud.grant.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val Int.DKK: Long get() = toLong() * 1_000_000
fun Long.creditsToDKK(): Long = this / 1_000_000


@Serializable
data class UpdateApplicationState(val applicationId: Long, val newState: GrantApplication.State, val notify: Boolean = true)
typealias UpdateApplicationStateResponse = Unit


@Serializable
data class CloseApplicationRequest(val applicationId: Long)
typealias CloseApplicationResponse = Unit

@Serializable
data class TransferApplicationRequest(val applicationId: Long, val transferToProjectId: String) //TODO()
typealias TransferApplicationResponse = Unit

@Serializable
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
typealias SubmitApplicationResponse = List<FindByLongId>

@Serializable
data class EditApplicationRequest(
    val applicationId: Long,
    val document: GrantApplication.Document
)
typealias EditApplicationResponse = Unit

@Serializable
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
data class GrantApplication(
    @UCloudApiDoc("""
        A unique identifier representing a GrantApplication
    
        The ID is used for all requests which manipulate the application. The ID is issued by UCloud/Core when the
        initial revision is submitted. The ID is never re-used by the system, even if a previous version has been
        closed.
    """)
    val id: String,

    @UCloudApiDoc("Username of the user who originially submitted the application")
    val requestedBy: String,
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
    sealed class Form {

        @Serializable
        @SerialName("plain_text")
        data class PlainText(
            @UCloudApiDoc("The template provided for new grant applications when the grant requester is a personal project")
            val personalProject: String,

            @UCloudApiDoc("The template provided for new grant applications when the grant requester is a new project")
            val newProject: String,

            @UCloudApiDoc("The template provided for new grant applications when the grant requester is an existing project")
            val existingProject: String
        ): Form()
    }
    @Serializable
    @SerialName("recipient")
    sealed class Recipient {
        data class ExistingProject(val id: String) : Recipient()
        data class NewProject(val title: String) : Recipient()
        data class PersonalWorkspace(val username: String) : Recipient()

        companion object {
            const val PERSONAL_TYPE = "personal"
            const val EXISTING_PROJECT_TYPE = "existing_project"
            const val NEW_PROJECT_TYPE = "new_project"
        }
    }
    @Serializable
    @SerialName("allocation_request")
    data class AllocationRequest(
        val category: String,
        val provider: String,
        val grantGiver: String,
        val balanceRequested: Long? = null,
        val sourceAllocation: String? = null,
        val period: Period,
    )
    @Serializable
    @SerialName("period")
    data class Period(
        val start: Long?,
        val end: Long?,
    )
    @Serializable
    @SerialName("status")
    data class Status(
        val overallState: State,
        val stateBreakdown: List<GrantGiverApprovalState>,
        val comments: List<Comment>,
        val revisions: List<Revision>,
    )
    @Serializable
    @SerialName("grant_giver_approval_state")
    data class GrantGiverApprovalState(
        val projectId: String,
        val projectTitle: String,
        val state: State,
    )
    @Serializable
    @SerialName("state")
    enum class State {
        APPROVED,
        REJECTED,
        CLOSED,
        IN_PROGRESS
    }
    @Serializable
    @SerialName("comment")
    data class Comment(
        val id: String,
        val username: String,
        val createdAt: Long,
        val comment: String,
    )
}
@Serializable
data class ViewApplicationRequest(val id: Long)
typealias ViewApplicationResponse = GrantApplication

@Serializable
data class BrowseProjectsRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias BrowseProjectsResponse = PageV2<ProjectWithTitle>
@Serializable
data class ProjectWithTitle(val projectId: String, val title: String)

@Serializable
data class GrantsBrowseAffiliationsRequest(
    val grantId: Long,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias GrantsBrowseAffiliationsResponse = PageV2<ProjectWithTitle>

@Serializable
data class GrantsBrowseProductsRequest(
    val projectId: String,
    val recipientType: String,
    val recipientId: String,
    val showHidden: Boolean = true
)

@Serializable
data class GrantsBrowseProductsResponse(
    val availableProducts: List<Product>
)

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
`Application` goes through the following steps:

1. User submits application to relevant project using `Grants.submitApplication`
2. Project administrator of `Application.resourcesOwnedBy` reviews the application
   - User and reviewer can comment on the application via `Grants.commentOnApplication`
   - User and reviewer can perform edits to the application via `Grants.editApplication`
3. Reviewer either performs `Grants.closeApplication` or `Grants.approveApplication`
4. If the `Application` was approved then resources are granted to the `Application.grantRecipient`

${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    /**
     *
     *
     *
     *
     * @see isEnabled
     * @see setEnabledStatus
     */

    val submitApplication =
        call<BulkRequest<SubmitApplicationRequest>, SubmitApplicationResponse, CommonErrorMessage>("submitApplication") {
            httpCreate(
                baseContext,
                "submit-application"
            )

            documentation {
                summary = "Submits an [Application] to a project"
                description = """
                     In order for the user to submit an application they must match any criteria in
                     [ProjectApplicationSettings.allowRequestsFrom]. If they are not the request will fail.
                """.trimIndent()
            }
        }


    val updateApplicationState = call<BulkRequest<UpdateApplicationState>, UpdateApplicationStateResponse, CommonErrorMessage>("updateApplicationState") {
        httpUpdate(
            baseContext,
            "update-state"
        )

       documentation {
            summary = "Approves or rejects an existing [GrantApplication]. If accepted by all grant givers this will " +
                "trigger granting of resources to the [GrantApplication.Document.recipient ]. "
            description = "Only the grant reviewer can perform this action."
        }
    }


    val editApplication = call<BulkRequest<EditApplicationRequest>, EditApplicationResponse, CommonErrorMessage>(
        "editApplication"
    ) {
        httpUpdate(
            baseContext,
            "edit"
        )

        documentation {
            summary = "Performs an edit to an existing [GrantApplication]"
            description = "Both the creator and any of the grant reviewers are allowed to edit the application."
        }
    }

    val closeApplication = call<BulkRequest<CloseApplicationRequest>, CloseApplicationResponse, CommonErrorMessage>(
        "closeApplication"
    ) {
        httpUpdate(
            baseContext,
            "close"
        )

        documentation {
            summary = "Closes an existing [GrantApplication]"
            description = "This action is identical to [rejectApplication] except it can be performed by the " +
                "[GrantApplication] creator."
        }
    }

    val transferApplication =
        call<BulkRequest<TransferApplicationRequest>, TransferApplicationResponse, CommonErrorMessage>("transferApplication") {
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
        call<BrowseApplicationsRequest, BrowseApplicationsResponse, CommonErrorMessage>("browseApplications") {
            httpBrowse(
                baseContext
            )

            documentation {
                summary = "List active [GrantApplication]s"
                description = """Lists active [GrantApplication]s which are relevant to a project. By using
                    [BrowseApplicationFlags] it is possible to filter on ingoing and/or outgoing.
                """.trimIndent()
            }
        }

    val browseProjects = call<BrowseProjectsRequest, BrowseProjectsResponse, CommonErrorMessage>("browseProjects") {
        httpBrowse(
            baseContext,
            "projects"
        )

        documentation {
            summary = "Endpoint for users to browse projects which they can send grant [Application]s to"
            description = """
                 Concretely, this will return a list for which the user matches the criteria listed in
                 [ProjectApplicationSettings.allowRequestsFrom].
            """.trimIndent()
        }
    }

    val browseAffiliations = call<
            GrantsBrowseAffiliationsRequest,
            GrantsBrowseAffiliationsResponse,
            CommonErrorMessage
            >("retrieveAffiliations") {
        httpBrowse(
            baseContext,
            "affiliations"
        )
    }

    val browseProducts = call<GrantsBrowseProductsRequest, GrantsBrowseProductsResponse, CommonErrorMessage>(
        "browseProducts"
    ) {
        httpBrowse(
            baseContext,
            "products"
        )
    }

    // This needs to be last
    val viewApplication = call<ViewApplicationRequest, ViewApplicationResponse, CommonErrorMessage>("viewApplication") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ViewApplicationRequest::id)
            }
        }

        documentation {
            summary = "Retrieves an active [Application]"
            description = """
                Only the creator and grant reviewers are allowed to view any given [Application].
            """.trimIndent()
        }
    }
}

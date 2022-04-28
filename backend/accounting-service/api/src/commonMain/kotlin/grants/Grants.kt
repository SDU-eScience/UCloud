package dk.sdu.cloud.grant.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.project.api.CreateProjectRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val Int.DKK: Long get() = toLong() * 1_000_000
fun Long.creditsToDKK(): Long = this / 1_000_000

@Serializable
data class UploadTemplatesRequest(
    val form: GrantApplication.Form
)

typealias UploadTemplatesResponse = Unit

@Serializable
data class UploadLogoRequest(
    val projectId: String,
)

typealias UploadLogoResponse = Unit

@Serializable
data class FetchLogoRequest(
    val projectId: String
)

typealias FetchLogoResponse = Unit

@Serializable
data class UploadDescriptionRequest(
    val projectId: String,
    val description: String
) {
    init {
        if (description.length > 240) {
            throw RPCException("Description must not exceed 240 characters", HttpStatusCode.BadRequest)
        }
    }
}
typealias UploadDescriptionResponse = Unit

@Serializable
data class FetchDescriptionRequest(
    val projectId: String
)
@Serializable
data class FetchDescriptionResponse(
    val description: String
)

@Serializable
data class ReadTemplatesRequest(val projectId: String)
typealias ReadTemplatesResponse = UploadTemplatesRequest

@Serializable
@UCloudApiDoc("""
    Describes some criteria which match a user
    
    This is used in conjunction with actions that require authorization.
""")
@UCloudApiOwnedBy(Grants::class)
sealed class UserCriteria {
    @Serializable
    @SerialName(UserCriteria.ANYONE_TYPE)
    @UCloudApiDoc("Matches any user")
    class Anyone : UserCriteria() {
        override fun equals(other: Any?): Boolean {
            return other is Anyone
        }

        override fun hashCode(): Int {
            return this::class.hashCode()
        }
    }

    @UCloudApiDoc("Matches any user with an email domain equal to `domain`")
    @Serializable
    @SerialName(UserCriteria.EMAIL_TYPE)
    data class EmailDomain(val domain: String) : UserCriteria()

    @UCloudApiDoc("""
       Matches any user with an organization matching [org] 
       
       The organization is currently derived from the information we receive from WAYF.
    """)
    @Serializable
    @SerialName(UserCriteria.WAYF_TYPE)
    data class WayfOrganization(val org: String) : UserCriteria()

    companion object {
        const val ANYONE_TYPE = "anyone"
        const val EMAIL_TYPE = "email"
        const val WAYF_TYPE = "wayf"
    }
}

@UCloudApiDoc("""
    Settings which control if an Application should be automatically approved
     
    The `Application` will be automatically approved if the all of the following is true:
    - The requesting user matches any of the criteria in `from`
    - The user has only requested resources (`Application.requestedResources`) which are present in `maxResources`
    - None of the resource requests exceed the numbers specified in `maxResources`
""")
@Serializable
data class AutomaticApprovalSettings(
    val from: List<UserCriteria>,
    val maxResources: List<GrantApplication.AllocationRequest>
)

@UCloudApiDoc("""
    Settings for grant Applications
     
    A user will be allowed to apply for grants to this project if they match any of the criteria listed in
    `allowRequestsFrom`.
""")
@Serializable
data class ProjectApplicationSettings(
    val automaticApproval: AutomaticApprovalSettings,
    val allowRequestsFrom: List<UserCriteria>,
    val excludeRequestsFrom: List<UserCriteria>
)

typealias UploadRequestSettingsRequest = ProjectApplicationSettings
typealias UploadRequestSettingsResponse = Unit

@Serializable
data class ReadRequestSettingsRequest(val projectId: String)
typealias ReadRequestSettingsResponse = ProjectApplicationSettings

@Serializable
data class ApproveApplicationRequest(val requestId: Long)
typealias ApproveApplicationResponse = Unit

@Serializable
data class RejectApplicationRequest(val requestId: Long, val notify: Boolean? = true) //TODO()
typealias RejectApplicationResponse = Unit

@Serializable
data class CloseApplicationRequest(val requestId: Long)
typealias CloseApplicationResponse = Unit

@Serializable
data class TransferApplicationRequest(val applicationId: Long, val transferToProjectId: String) //TODO()
typealias TransferApplicationResponse = Unit

@Serializable
data class CommentOnApplicationRequest(val requestId: Long, val comment: String)
typealias CommentOnApplicationResponse = Unit

@Serializable
data class DeleteCommentRequest(val commentId: Long)
typealias DeleteCommentResponse = Unit

@Serializable
enum class GrantApplicationFilter {
    SHOW_ALL,
    ACTIVE,
    INACTIVE
}

@Serializable
data class IngoingApplicationsRequest(
    val filter: GrantApplicationFilter = GrantApplicationFilter.ACTIVE,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias IngoingApplicationsResponse = PageV2<GrantApplication>

@Serializable
data class ApplicationWithComments(val application: GrantApplication, val comments: List<GrantApplication.Comment>, val approver: Boolean)

@Serializable
data class OutgoingApplicationsRequest(
    val filter: GrantApplicationFilter = GrantApplicationFilter.ACTIVE,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias OutgoingApplicationsResponse = PageV2<GrantApplication>

typealias SubmitApplicationRequest = CreateApplication
typealias SubmitApplicationResponse = FindByLongId

@Serializable
data class EditReferenceIdRequest(
    val id: Long,
    val newReferenceId: String?
) {
    init {
        val errorMessage = "DeiC is a reserved keyword."
        val deicUniList = listOf("KU", "DTU", "AU", "SDU", "AAU", "RUC", "ITU", "CBS")
        if (newReferenceId != null) {
            if (newReferenceId.lowercase().startsWith("deic")) {
                val splitId = newReferenceId.split("-")
                when  {
                    splitId.size != 4 -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage It seems like you are not following request format. DeiC-XX-YY-NUMBER"
                        )
                    }
                    splitId.first() != "DeiC" -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage First part should be DeiC."
                        )
                    }
                    !deicUniList.contains(splitId[1]) -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage Uni value is not listed in DeiC. If you think this is a mistake, please contact UCloud"
                        )
                    }
                    splitId[2].length != 2 -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Allocation category wrong fornat"
                        )
                    }
                    !splitId[2].contains(Regex("""[LNSI][1-5]$""")) -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Allocation category has wrong format."
                        )
                    }
                    !splitId[3].contains(Regex("""^\d+$""")) ->
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Only supports numeric local ids"
                        )
                }
            }
        }
    }
}


typealias EditReferenceIdResponse = Unit

@Serializable
data class EditApplicationRequest(
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
    )
    @Serializable
    @SerialName("form")
    sealed class Form {

        @Serializable
        @SerialName("plain_text")
        class PlainText(
            @UCloudApiDoc("The template provided for new grant applications when the grant requester is a personal project")
            val personalProject: String,

            @UCloudApiDoc("The template provided for new grant applications when the grant requester is a new project")
            val newProject: String,

            @UCloudApiDoc("The template provided for new grant applications when the grant requester is an existing project")
            val existingProject: String
        )
    }
    @Serializable
    @SerialName("recipient")
    sealed class Recipient {
        data class ExistingProject(val id: String) : Recipient()
        data class NewProject(val title: String) : Recipient()
        data class PersonalWorkspace(val username: String) : Recipient()
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
        val id: String,
        val title: String,
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
typealias ViewApplicationResponse = ApplicationWithComments

@Serializable
data class SetEnabledStatusRequest(val projectId: String, val enabledStatus: Boolean)
typealias SetEnabledStatusResponse = Unit

@Serializable
data class IsEnabledRequest(val projectId: String)
@Serializable
data class IsEnabledResponse(val enabled: Boolean)

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
data class GrantsRetrieveAffiliationsRequest(
    val grantId: Long,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias GrantsRetrieveAffiliationsResponse = PageV2<ProjectWithTitle>

@Serializable
data class GrantsRetrieveProductsRequest(
    val projectId: String,
    val recipientType: String,
    val recipientId: String,
    val showHidden: Boolean = true
)

@Serializable
data class GrantsRetrieveProductsResponse(
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
     * Uploads a description of a project which is enabled
     *
     * Only project administrators of the project can upload a description
     *
     * @see setEnabledStatus
     * @see isEnabled
     */
    val uploadDescription = call<UploadDescriptionRequest, UploadDescriptionResponse, CommonErrorMessage>("uploadDescription") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"uploadDescription"
            }

            body { bindEntireRequestFromBody() }

        }
    }

    val fetchDescription = call<FetchDescriptionRequest, FetchDescriptionResponse, CommonErrorMessage>("fetchDescription") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"description"
            }

            params {
                +boundTo(FetchDescriptionRequest::projectId)
            }
        }

        documentation {
            summary = "Fetches a description of a project"
        }
    }

    val uploadLogo = call<UploadLogoRequest, UploadLogoResponse, CommonErrorMessage>("uploadLogo") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"uploadLogo"
            }

            headers {
                +boundTo("Upload-Name", UploadLogoRequest::projectId)
            }

            /*
            body {
                bindToSubProperty(UploadLogoRequest::data)
            }
             */
        }

        documentation {
            summary = "Uploads a logo for a project, which is enabled"
            description = "Only project administrators of the project can upload a logo"
        }
    }

    val fetchLogo = call<FetchLogoRequest, FetchLogoResponse, CommonErrorMessage>("fetchLogo") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"logo"
            }

            params {
                +boundTo(FetchLogoRequest::projectId)
            }
        }

        documentation {
            summary = "Fetches a logo for a project"
        }
    }

    /**
     *
     *
     *
     *
     * @see isEnabled
     * @see setEnabledStatus
     */
    val uploadTemplates = call<BulkRequest<UploadTemplatesRequest>, UploadTemplatesResponse, CommonErrorMessage>("uploadTemplates") {
        httpUpdate(
            baseContext,
            "upload-templates"
        )

        documentation {
            summary = "Uploads templates used for new grant Applications"
            description = "Only project administrators of the project can upload new templates. The project needs to be " +
                "enabled."
        }
    }

    val uploadRequestSettings =
        call<BulkRequest<UploadRequestSettingsRequest>, UploadRequestSettingsResponse, CommonErrorMessage>("uploadRequestSettings") {
            httpUpdate(
                baseContext,
                "request-settings"
            )

            documentation {
                summary = "Uploads [ProjectApplicationSettings] to be associated with a project. The project must be " +
                    "enabled."
            }
        }

    val readRequestSettings =
        call<ReadRequestSettingsRequest, ReadRequestSettingsResponse, CommonErrorMessage>("readRequestSettings") {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"request-settings"
                }

                params {
                    +boundTo(ReadRequestSettingsRequest::projectId)
                }
            }
        }

    val readTemplates = call<ReadTemplatesRequest, ReadTemplatesResponse, CommonErrorMessage>("readTemplates") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"read-templates"
            }

            params {
                +boundTo(ReadTemplatesRequest::projectId)
            }
        }

        documentation {
            summary = "Reads the templates for a new grant [Application]"
            description = """
                User interfaces should display the relevant template, based on who will be the 
                [Application.grantRecipient].
            """.trimIndent()
        }
    }

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

    val commentOnApplication =
        call<BulkRequest<CommentOnApplicationRequest>, CommentOnApplicationResponse, CommonErrorMessage>("commentOnApplication") {
            httpCreate(
                baseContext,
                "comment"
            )

            documentation {
                summary = "Adds a comment to an existing [GrantApplication]"
                description = """
                    Only the [GrantApplication] creator and [GrantApplication] reviewers are allowed to comment on the 
                    [GrantApplication].
                """.trimIndent()
            }
        }

    val deleteComment = call<BulkRequest<DeleteCommentRequest>, DeleteCommentResponse, CommonErrorMessage>("deleteComment") {
        httpDelete(
            "$baseContext/comment"
        )


        documentation {
            summary = "Deletes a comment from an existing [GrantApplication]"
            description = """
                The comment can only be deleted by the author of the comment.
            """.trimIndent()
        }
    }

    val approveApplication = call<BulkRequest<ApproveApplicationRequest>, ApproveApplicationResponse, CommonErrorMessage>("approveApplication") {
        httpUpdate(
            baseContext,
            "approve"
        )

       documentation {
            summary = "Approves an existing [GrantApplication] this will trigger granting of resources to the " +
                "[GrantApplication.Document.recipient ]"
            description = "Only the grant reviewer can perform this action."
        }
    }

    val rejectApplication = call<BulkRequest<RejectApplicationRequest>, RejectApplicationResponse, CommonErrorMessage>("rejectApplication") {
        httpUpdate(
            baseContext,
            "reject"
        )

        documentation {
            summary = "Rejects an [Application]"
            description = """
                 The [Application] cannot receive any new change to it and the [Application] creator must re-submit the
                 [Application].
                 
                 Only the grant reviewer can perform this action.
            """.trimIndent()
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

    val editReferenceId = call<BulkRequest<EditReferenceIdRequest>, EditReferenceIdResponse, CommonErrorMessage>("editReferenceId") {
        httpUpdate(baseContext, "editReference")

        documentation {
            summary = "Performs an edit to an existing [referenceId]"
            description = "Any of the grant reviewers are allowed to edit the reference id."
        }
    }

    val closeApplication = call<CloseApplicationRequest, CloseApplicationResponse, CommonErrorMessage>(
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

    val ingoingApplications =
        call<IngoingApplicationsRequest, IngoingApplicationsResponse, CommonErrorMessage>("ingoingApplications") {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"ingoing"
                }

                params {

                    +boundTo(OutgoingApplicationsRequest::itemsPerPage)
                    +boundTo(OutgoingApplicationsRequest::consistency)
                    +boundTo(OutgoingApplicationsRequest::next)
                    +boundTo(OutgoingApplicationsRequest::itemsToSkip)
                    +boundTo(OutgoingApplicationsRequest::filter)
                }
            }

            documentation {
                summary = "Lists active [GrantApplication]s which are 'ingoing' (received by) to a project"
            }
        }

    val outgoingApplications =
        call<OutgoingApplicationsRequest, OutgoingApplicationsResponse, CommonErrorMessage>("outgoingApplications") {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"outgoing"
                }

                params {
                    +boundTo(OutgoingApplicationsRequest::itemsPerPage)
                    +boundTo(OutgoingApplicationsRequest::consistency)
                    +boundTo(OutgoingApplicationsRequest::next)
                    +boundTo(OutgoingApplicationsRequest::itemsToSkip)
                    +boundTo(OutgoingApplicationsRequest::filter)
                }
            }

            documentation {
                summary = "Lists all active [GrantApplication]s made by the calling user"
            }
        }

    val setEnabledStatus =
        call<BulkRequest<SetEnabledStatusRequest>, SetEnabledStatusResponse, CommonErrorMessage>("setEnabledStatus") {
            httpUpdate(
                baseContext,
                "set-enabled",
                Roles.PRIVILEGED
            )

            documentation {
                summary = "Enables a project to receive [Application]"
                description = """
                     Note that a project will not be able to receive any applications until its
                     [ProjectApplicationSettings.allowRequestsFrom] allow for it.
                """.trimIndent()
            }
        }

    val isEnabled = call<IsEnabledRequest, IsEnabledResponse, CommonErrorMessage>("isEnabled") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"is-enabled"
            }

            params {
                +boundTo(IsEnabledRequest::projectId)
            }
        }

        documentation {
            summary = "If this returns true then the project (as specified by [IsEnabledRequest.projectId]) can receive " +
                "grant [Application]s."
        }
    }

    val browseProjects = call<BrowseProjectsRequest, BrowseProjectsResponse, CommonErrorMessage>("browseProjects") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"browse-projects"
            }

            params {
                +boundTo(BrowseProjectsRequest::itemsPerPage)
                +boundTo(BrowseProjectsRequest::consistency)
                +boundTo(BrowseProjectsRequest::next)
                +boundTo(BrowseProjectsRequest::itemsToSkip)
            }
        }

        documentation {
            summary = "Endpoint for users to browse projects which they can send grant [Application]s to"
            description = """
                 Concretely, this will return a list for which the user matches the criteria listed in
                 [ProjectApplicationSettings.allowRequestsFrom].
            """.trimIndent()
        }
    }

    val retrieveAffiliations = call<
            GrantsRetrieveAffiliationsRequest,
            GrantsRetrieveAffiliationsResponse,
            CommonErrorMessage
            >("retrieveAffiliations") {
        auth {
            access = AccessRight.READ
        }
        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"retrieveAffiliations"
            }

            params {
                +boundTo(GrantsRetrieveAffiliationsRequest::grantId)
                +boundTo(GrantsRetrieveAffiliationsRequest::itemsPerPage)
                +boundTo(GrantsRetrieveAffiliationsRequest::next)
                +boundTo(GrantsRetrieveAffiliationsRequest::itemsToSkip)
                +boundTo(GrantsRetrieveAffiliationsRequest::consistency)
            }
        }
    }

    val retrieveProducts = call<GrantsRetrieveProductsRequest, GrantsRetrieveProductsResponse, CommonErrorMessage>(
        "retrieveProducts"
    ) {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"retrieveProducts"
            }

            params {
                +boundTo(GrantsRetrieveProductsRequest::projectId)
                +boundTo(GrantsRetrieveProductsRequest::recipientId)
                +boundTo(GrantsRetrieveProductsRequest::recipientType)
                +boundTo(GrantsRetrieveProductsRequest::showHidden)
            }
        }
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

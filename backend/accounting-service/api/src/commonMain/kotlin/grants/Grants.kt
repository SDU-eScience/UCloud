package dk.sdu.cloud.grant.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.project.api.CreateProjectRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

val Int.DKK: Long get() = toLong() * 1_000_000
fun Long.creditsToDKK(): Long = this / 1_000_000

@Serializable
data class UploadTemplatesRequest(
    @UCloudApiDoc("The template provided for new grant applications when the grant requester is a personal project")
    val personalProject: String,

    @UCloudApiDoc("The template provided for new grant applications when the grant requester is a new project")
    val newProject: String,

    @UCloudApiDoc("The template provided for new grant applications when the grant requester is an existing project")
    val existingProject: String
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
    val maxResources: List<ResourceRequest>
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
data class RejectApplicationRequest(val requestId: Long, val notify: Boolean? = true)
typealias RejectApplicationResponse = Unit

@Serializable
data class CloseApplicationRequest(val requestId: Long)
typealias CloseApplicationResponse = Unit

@Serializable
data class TransferApplicationRequest(val applicationId: Long, val transferToProjectId: String)
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
typealias IngoingApplicationsResponse = PageV2<Application>

@Serializable
data class Comment(val id: Long, val postedBy: String, val postedAt: Long, val comment: String)
@Serializable
data class ApplicationWithComments(val application: Application, val comments: List<Comment>, val approver: Boolean)

@Serializable
data class OutgoingApplicationsRequest(
    val filter: GrantApplicationFilter = GrantApplicationFilter.ACTIVE,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2
typealias OutgoingApplicationsResponse = PageV2<Application>

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
    val id: Long,
    val newDocument: String,
    val newResources: List<ResourceRequest>
)
typealias EditApplicationResponse = Unit

@Serializable
enum class ApplicationStatus {
    APPROVED,
    REJECTED,
    CLOSED,
    IN_PROGRESS
}

@Serializable
sealed class GrantRecipient {
    @Serializable
    @SerialName(GrantRecipient.PERSONAL_TYPE)
    data class PersonalProject(val username: String) : GrantRecipient()

    @Serializable
    @SerialName(GrantRecipient.EXISTING_PROJECT_TYPE)
    data class ExistingProject(val projectId: String) : GrantRecipient()

    @Serializable
    @SerialName(GrantRecipient.NEW_PROJECT_TYPE)
    data class NewProject(val projectTitle: String) : GrantRecipient() {
        init {
            CreateProjectRequest(projectTitle, null) // Trigger validation
        }
    }

    companion object {
        const val PERSONAL_TYPE = "personal"
        const val EXISTING_PROJECT_TYPE = "existing_project"
        const val NEW_PROJECT_TYPE = "new_project"
    }
}

@Serializable
@UCloudApiOwnedBy(Grants::class)
data class ResourceRequest(
    val productCategory: String,
    val productProvider: String,
    val balanceRequested: Long? = null,
) {
    init {
        if (balanceRequested != null && balanceRequested < 0) {
            throw RPCException("Cannot request a negative amount of resources", HttpStatusCode.BadRequest)
        }
    }

    companion object {
        fun fromProduct(product: Product.Compute, credits: Long): ResourceRequest {
            return ResourceRequest(product.category.name, product.category.provider, credits)
        }

        fun fromProduct(product: Product.Storage, credits: Long, quota: Long): ResourceRequest {
            return ResourceRequest(product.category.name, product.category.provider, credits)
        }
    }
}

@Serializable
data class CreateApplication(
    val resourcesOwnedBy: String, // Project ID of the project owning the resources
    val grantRecipient: GrantRecipient,
    val document: String,
    val requestedResources: List<ResourceRequest> // This is _always_ additive to existing resources
) {
    init {
        if (requestedResources.isEmpty()) {
            throw RPCException("You must request at least one resource", HttpStatusCode.BadRequest)
        }
    }
}

@Serializable
data class Application(
    val status: ApplicationStatus,
    val resourcesOwnedBy: String, // Project ID of the project owning the resources
    val requestedBy: String, // Username of user submitting the request
    val grantRecipient: GrantRecipient,
    val document: String,
    val requestedResources: List<ResourceRequest>, // This is _always_ additive to existing resources
    val id: Long,
    val resourcesOwnedByTitle: String,
    val grantRecipientPi: String,
    val grantRecipientTitle: String,
    val createdAt: Long,
    val updatedAt: Long,
    val statusChangedBy: String? = null,
    val referenceId: String? = null
)

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
    val uploadDescription = call("uploadDescription", UploadDescriptionRequest.serializer(), UploadDescriptionResponse.serializer(), CommonErrorMessage.serializer()) {
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

    val fetchDescription = call("fetchDescription", FetchDescriptionRequest.serializer(), FetchDescriptionResponse.serializer(), CommonErrorMessage.serializer()) {
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

    val uploadLogo = call("uploadLogo", UploadLogoRequest.serializer(), UploadLogoResponse.serializer(), CommonErrorMessage.serializer()) {
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

    val fetchLogo = call("fetchLogo", FetchLogoRequest.serializer(), FetchLogoResponse.serializer(), CommonErrorMessage.serializer()) {
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
    val uploadTemplates = call("uploadTemplates", UploadTemplatesRequest.serializer(), UploadTemplatesResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"upload-templates"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Uploads templates used for new grant Applications"
            description = "Only project administrators of the project can upload new templates. The project needs to be " +
                "enabled."
        }
    }

    val uploadRequestSettings = call("uploadRequestSettings", UploadRequestSettingsRequest.serializer(), UploadRequestSettingsResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.END_USER + Roles.SERVICE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"request-settings"
                }

                body { bindEntireRequestFromBody() }
            }

            documentation {
                summary = "Uploads [ProjectApplicationSettings] to be associated with a project. The project must be " +
                    "enabled."
            }
        }

    val readRequestSettings = call("readRequestSettings", ReadRequestSettingsRequest.serializer(), ReadRequestSettingsResponse.serializer(), CommonErrorMessage.serializer()) {
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

    val readTemplates = call("readTemplates", ReadTemplatesRequest.serializer(), ReadTemplatesResponse.serializer(), CommonErrorMessage.serializer()) {
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

    val submitApplication = call("submitApplication", SubmitApplicationRequest.serializer(), SubmitApplicationResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"submit-application"
                }

                body { bindEntireRequestFromBody() }
            }

            documentation {
                summary = "Submits an [Application] to a project"
                description = """
                     In order for the user to submit an application they must match any criteria in
                     [ProjectApplicationSettings.allowRequestsFrom]. If they are not the request will fail.
                """.trimIndent()
            }
        }

    val commentOnApplication = call("commentOnApplication", CommentOnApplicationRequest.serializer(), CommentOnApplicationResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"comment"
                }

                body { bindEntireRequestFromBody() }
            }

            documentation {
                summary = "Adds a comment to an existing [Application]"
                description = """
                    Only the [Application] creator and [Application] reviewers are allowed to comment on the 
                    [Application].
                """.trimIndent()
            }
        }

    val deleteComment = call("deleteComment", DeleteCommentRequest.serializer(), DeleteCommentResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +"comment"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Deletes a comment from an existing [Application]"
            description = """
                The comment can only be deleted by the author of the comment.
            """.trimIndent()
        }
    }

    val approveApplication = call("approveApplication", ApproveApplicationRequest.serializer(), ApproveApplicationResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.END_USER + Roles.SERVICE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"approve"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Approves an existing [Application] this will trigger granting of resources to the " +
                "[Application.grantRecipient]"
            description = "Only the grant reviewer can perform this action."
        }
    }

    val rejectApplication = call("rejectApplication", RejectApplicationRequest.serializer(), RejectApplicationResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"reject"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Rejects an [Application]"
            description = """
                 The [Application] cannot receive any new change to it and the [Application] creator must re-submit the
                 [Application].
                 
                 Only the grant reviewer can perform this action.
            """.trimIndent()
        }
    }

    val editApplication = call("editApplication", EditApplicationRequest.serializer(), EditApplicationResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"edit"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Performs an edit to an existing [Application]"
            description = "Both the creator and any of the grant reviewers are allowed to edit the application."
        }
    }

    val editReferenceId = call("editReferenceId", EditReferenceIdRequest.serializer(), EditReferenceIdResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "editReference")
    }

    val closeApplication = call("closeApplication", CloseApplicationRequest.serializer(), CloseApplicationResponse.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"close"
            }

            body { bindEntireRequestFromBody() }
        }

        documentation {
            summary = "Closes an existing [Application]"
            description = "This action is identical to [rejectApplication] except it can be performed by the " +
                "[Application] creator."
        }
    }

    val transferApplication = call("transferApplication", TransferApplicationRequest.serializer(), TransferApplicationResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.AUTHENTICATED
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"transfer"
                }

                body { bindEntireRequestFromBody()}
            }

            documentation {
                summary = "Transfers application to other root project"
            }
        }

    val ingoingApplications = call("ingoingApplications", IngoingApplicationsRequest.serializer(), PageV2.serializer(Application.serializer()), CommonErrorMessage.serializer()) {
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
                summary = "Lists active [Application]s which are 'ingoing' (received by) to a project"
            }
        }

    val outgoingApplications = call("outgoingApplications", OutgoingApplicationsRequest.serializer(), PageV2.serializer(Application.serializer()), CommonErrorMessage.serializer()) {
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
                summary = "Lists all active [Application]s made by the calling user"
            }
        }

    val setEnabledStatus = call("setEnabledStatus", SetEnabledStatusRequest.serializer(), SetEnabledStatusResponse.serializer(), CommonErrorMessage.serializer()) {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.PRIVILEGED
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"set-enabled"
                }

                body { bindEntireRequestFromBody() }
            }

            documentation {
                summary = "Enables a project to receive [Application]"
                description = """
                     Note that a project will not be able to receive any applications until its
                     [ProjectApplicationSettings.allowRequestsFrom] allow for it.
                """.trimIndent()
            }
        }

    val isEnabled = call("isEnabled", IsEnabledRequest.serializer(), IsEnabledResponse.serializer(), CommonErrorMessage.serializer()) {
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

    val browseProjects = call("browseProjects", BrowseProjectsRequest.serializer(), PageV2.serializer(ProjectWithTitle.serializer()), CommonErrorMessage.serializer()) {
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

    val retrieveAffiliations = call("retrieveAffiliations", GrantsRetrieveAffiliationsRequest.serializer(), PageV2.serializer(ProjectWithTitle.serializer()), CommonErrorMessage.serializer()) {
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

    val retrieveProducts = call("retrieveProducts", GrantsRetrieveProductsRequest.serializer(), GrantsRetrieveProductsResponse.serializer(), CommonErrorMessage.serializer()) {
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
    val viewApplication = call("viewApplication", ViewApplicationRequest.serializer(), ViewApplicationResponse.serializer(), CommonErrorMessage.serializer()) {
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

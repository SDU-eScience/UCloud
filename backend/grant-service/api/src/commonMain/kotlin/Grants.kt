package dk.sdu.cloud.grant.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategory
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.project.api.CreateProjectRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val Int.DKK: Long get() = toLong() * 1_000_000
fun Long.creditsToDKK(): Long = this / 1_000_000

@Serializable
data class UploadTemplatesRequest(
    /**
     * The template provided for new grant applications when the grant requester is a personal project
     *
     * @see Application.grantRecipient
     * @see GrantRecipient.PersonalProject
     */
    val personalProject: String,

    /**
     * The template provided for new grant applications when the grant requester is a new project
     *
     * @see Application.grantRecipient
     * @see GrantRecipient.NewProject
     */
    val newProject: String,

    /**
     * The template provided for new grant applications when the grant requester is an existing project
     *
     * @see Application.grantRecipient
     * @see GrantRecipient.ExistingProject
     */
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

/**
 * A [UserCriteria] describes some criteria that matches a user. This is used in conjunction with actions that need
 * authorization.
 *
 * @see UserCriteria.Anyone
 * @see UserCriteria.EmailDomain
 * @see UserCriteria.WayfOrganization
 */
@Serializable
sealed class UserCriteria {
    /**
     * Matches any user
     */
    @Serializable
    @SerialName(UserCriteria.ANYONE_TYPE)
    class Anyone : UserCriteria()

    /**
     * Matches any user with an email domain equal to [domain]
     */
    @Serializable
    @SerialName(UserCriteria.EMAIL_TYPE)
    data class EmailDomain(val domain: String) : UserCriteria()

    /**
     * Matches any user with an organization matching [org]
     *
     * The organization is currently derived from the information we receive from WAYF.
     */
    @Serializable
    @SerialName(UserCriteria.WAYF_TYPE)
    data class WayfOrganization(val org: String) : UserCriteria()

    companion object {
        const val ANYONE_TYPE = "anyone"
        const val EMAIL_TYPE = "email"
        const val WAYF_TYPE = "wayf"
    }
}

/**
 * Settings which control if an [Application] should be automatically approved
 *
 * The [Application] will be automatically approved if the all of the following is true:
 *  - The requesting user matches any of the criteria in [from]
 *  - The user has only requested resources ([Application.requestedResources]) which are present in [maxResources]
 *  - None of the resource requests exceed the numbers specified in [maxResources]
 */
@Serializable
data class AutomaticApprovalSettings(
    val from: List<UserCriteria>,
    val maxResources: List<ResourceRequest>
)

/**
 * Settings for grant [Application]s
 *
 * A user will be allowed to apply for grants to this project if they match any of the criteria listed in
 * [allowRequestsFrom].
 *
 * @see AutomaticApprovalSettings
 */
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
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
    val filter: GrantApplicationFilter = GrantApplicationFilter.ACTIVE
) : WithPaginationRequest
typealias IngoingApplicationsResponse = Page<Application>

@Serializable
data class Comment(val id: Long, val postedBy: String, val postedAt: Long, val comment: String)
@Serializable
data class ApplicationWithComments(val application: Application, val comments: List<Comment>, val approver: Boolean)

@Serializable
data class OutgoingApplicationsRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
    val filter: GrantApplicationFilter = GrantApplicationFilter.ACTIVE
) : WithPaginationRequest
typealias OutgoingApplicationsResponse = Page<Application>

typealias SubmitApplicationRequest = CreateApplication
typealias SubmitApplicationResponse = FindByLongId

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
    class PersonalProject(val username: String) : GrantRecipient()

    @Serializable
    @SerialName(GrantRecipient.EXISTING_PROJECT_TYPE)
    class ExistingProject(val projectId: String) : GrantRecipient()

    @Serializable
    @SerialName(GrantRecipient.NEW_PROJECT_TYPE)
    class NewProject(val projectTitle: String) : GrantRecipient() {
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
data class ResourceRequest(
    val productCategory: String,
    val productProvider: String,
    val creditsRequested: Long? = null,
    val quotaRequested: Long? = null,
) {
    init {
        if (creditsRequested != null && creditsRequested < 0) {
            throw RPCException("Cannot request a negative amount of resources", HttpStatusCode.BadRequest)
        }
        if (quotaRequested != null && quotaRequested < 0) {
            throw RPCException("Cannot request a negative quota", HttpStatusCode.BadRequest)
        }
    }

    companion object {
        fun fromProduct(product: Product.Compute, credits: Long): ResourceRequest {
            return ResourceRequest(product.category.id, product.category.provider, credits, null)
        }

        fun fromProduct(product: Product.Storage, credits: Long, quota: Long): ResourceRequest {
            return ResourceRequest(product.category.id, product.category.provider, credits, quota)
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
    override val page: Int? = null
) : WithPaginationRequest
typealias BrowseProjectsResponse = Page<ProjectWithTitle>
@Serializable
data class ProjectWithTitle(val projectId: String, val title: String)

@Serializable
data class GrantsRetrieveAffiliationsRequest(
    val grantId: Long,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias GrantsRetrieveAffiliationsResponse = Page<ProjectWithTitle>

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

/**
 * [Grants] provide a way for users of UCloud to apply for resources ([ResourceRequest]) for any [GrantRecipient]
 *
 * In order for any user to use UCloud they must have resources. Resources, see [dk.sdu.cloud.accounting.api.Wallets],
 * are required for use of any compute or storage. There are only two ways of receiving any credits, either through
 * an admin directly granting you the credits or by receiving them from a project
 * (see [dk.sdu.cloud.accounting.api.Wallets.setBalance] and [dk.sdu.cloud.accounting.api.Wallets.transferToPersonal]).
 *
 * The [Grants] service acts as a more user-friendly gateway to receiving resources from a project. Every [Application]
 * goes through the following steps:
 *
 * 1. User submits application to relevant project using [Grants.submitApplication]
 * 2. Project administrator of [Application.resourcesOwnedBy] reviews the application
 *    - User and reviewer can comment on the application via [Grants.commentOnApplication]
 *    - User and reviewer can perform edits to the application via [Grants.editApplication]
 * 3. Reviewer either performs [Grants.closeApplication] or [Grants.approveApplication]
 * 4. If the [Application] was approved then resources are granted to the [Application.grantRecipient]
 */
object Grants : CallDescriptionContainer("grant") {
    val baseContext = "/api/grant"

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

    /**
     * Fetches a description of a project
     */
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
    }

    /**
     * Uploads a logo for a project which is enabled
     *
     * Only project administrators of the project can upload a logo
     *
     * @see setEnabledStatus
     * @see isEnabled
     */
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
    }

    /**
     * Fetches a logo for a project
     */
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
    }

    /**
     * Uploads templates used for new grant [Application]s
     *
     * Only project administrators of the project can upload new templates. The project needs to be enabled.
     *
     * @see isEnabled
     * @see setEnabledStatus
     */
    val uploadTemplates = call<UploadTemplatesRequest, UploadTemplatesResponse, CommonErrorMessage>("uploadTemplates") {
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
    }

    /**
     * Uploads [ProjectApplicationSettings] to be associated with a project. The project must be enabled.
     *
     * @see isEnabled
     */
    val uploadRequestSettings =
        call<UploadRequestSettingsRequest, UploadRequestSettingsResponse, CommonErrorMessage>("uploadRequestSettings") {
            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"request-settings"
                }

                body { bindEntireRequestFromBody() }
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

    /**
     * Reads the templates for a new grant [Application]
     *
     * User interfaces should display the relevant template, based on who will be the [Application.grantRecipient].
     */
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
    }


    /**
     * Submits an [Application] to a project
     *
     * In order for the user to submit an application they must match any criteria in
     * [ProjectApplicationSettings.allowRequestsFrom]. If they are not the request will fail.
     */
    val submitApplication =
        call<SubmitApplicationRequest, SubmitApplicationResponse, CommonErrorMessage>("submitApplication") {
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
        }

    /**
     * Adds a comment to an existing [Application]
     *
     * Only the [Application] creator and [Application] reviewers are allowed to comment on the [Application].
     */
    val commentOnApplication =
        call<CommentOnApplicationRequest, CommentOnApplicationResponse, CommonErrorMessage>("commentOnApplication") {
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
        }

    /**
     * Deletes a comment from an existing [Application]
     *
     * The comment can only be deleted by the author of the comment.
     */
    val deleteComment = call<DeleteCommentRequest, DeleteCommentResponse, CommonErrorMessage>("deleteComment") {
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
    }

    /**
     * Approves an existing [Application] this will trigger granting of resources to the [Application.grantRecipient]
     *
     * Only the grant reviewer can perform this action.
     */
    val approveApplication = call<ApproveApplicationRequest, ApproveApplicationResponse, CommonErrorMessage>("approveApplication") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"approve"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Rejects an [Application]
     *
     * The [Application] cannot receive any new change to it and the [Application] creator must re-submit the
     * [Application].
     *
     * Only the grant reviewer can perform this action.
     */
    val rejectApplication = call<RejectApplicationRequest, RejectApplicationResponse, CommonErrorMessage>("rejectApplication") {
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
    }

    /**
     * Performs an edit to an existing [Application]
     *
     * Both the creator and any of the grant reviewers are allowed to edit the application.
     */
    val editApplication = call<EditApplicationRequest, EditApplicationResponse, CommonErrorMessage>(
        "editApplication"
    ) {
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
    }

    /**
     * Closes an existing [Application]
     *
     * This action is identical to [rejectApplication] except it can be performed by the [Application] creator.
     */
    val closeApplication = call<CloseApplicationRequest, CloseApplicationResponse, CommonErrorMessage>(
        "closeApplication"
    ) {
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
    }

    /**
     * Transfers application to other root project
     */
    val transferApplication =
        call<TransferApplicationRequest, TransferApplicationResponse, CommonErrorMessage>("transferApplication") {
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
        }

    /**
     * Lists active [Application]s which are 'ingoing' (received by) to a project
     */
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
                    +boundTo(IngoingApplicationsRequest::itemsPerPage)
                    +boundTo(IngoingApplicationsRequest::page)
                    +boundTo(IngoingApplicationsRequest::filter)
                }
            }
        }

    /**
     * Lists all active [Application]s made by the calling user
     */
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
                    +boundTo(OutgoingApplicationsRequest::page)
                    +boundTo(OutgoingApplicationsRequest::filter)
                }
            }
        }

    /**
     * Enables a project to receive [Application]
     *
     * Note that a project will not be able to receive any applications until its
     * [ProjectApplicationSettings.allowRequestsFrom] allow for it.
     */
    val setEnabledStatus =
        call<SetEnabledStatusRequest, SetEnabledStatusResponse, CommonErrorMessage>("setEnabledStatus") {
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
        }

    /**
     * If this returns true then the project (as specified by [IsEnabledRequest.projectId]) can receive grant
     * [Application]s.
     */
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
    }

    /**
     * Endpoint for users to browse projects which they can send grant [Application]s to
     *
     * Concretely, this will return a list for which the user matches the criteria listed in
     * [ProjectApplicationSettings.allowRequestsFrom].
     */
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
                +boundTo(BrowseProjectsRequest::page)
            }
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
                +boundTo(GrantsRetrieveAffiliationsRequest::page)
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
    /**
     * Retrieves an active [Application]
     *
     * Only the creator and grant reviewers are allowed to view any given [Application].
     */
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
    }
}

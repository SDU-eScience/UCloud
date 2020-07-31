package dk.sdu.cloud.grant.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.TYPE_PROPERTY
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

val Int.DKK: Long get() = toLong() * 1_000_000
fun Long.creditsToDKK(): Long = this / 1_000_000

data class UploadTemplatesRequest(
    val personalProject: String,
    val newProject: String,
    val existingProject: String
)

typealias UploadTemplatesResponse = Unit

data class ReadTemplatesRequest(val projectId: String)
typealias ReadTemplatesResponse = UploadTemplatesRequest

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = UserCriteria.Anyone::class, name = UserCriteria.ANYONE_TYPE),
    JsonSubTypes.Type(value = UserCriteria.EmailDomain::class, name = UserCriteria.EMAIL_TYPE),
    JsonSubTypes.Type(value = UserCriteria.WayfOrganization::class, name = UserCriteria.WAYF_TYPE)
)
sealed class UserCriteria {
    class Anyone : UserCriteria()
    data class EmailDomain(val domain: String) : UserCriteria()
    data class WayfOrganization(val org: String) : UserCriteria()

    companion object {
        const val ANYONE_TYPE = "anyone"
        const val EMAIL_TYPE = "email"
        const val WAYF_TYPE = "wayf"
    }
}

data class AutomaticApprovalSettings(
    val from: List<UserCriteria>,
    val maxResources: List<ResourceRequest>
)

data class ProjectApplicationSettings(
    val automaticApproval: AutomaticApprovalSettings,
    val allowRequestsFrom: List<UserCriteria>
)

typealias UploadRequestSettingsRequest = ProjectApplicationSettings
typealias UploadRequestSettingsResponse = Unit

data class ReadRequestSettingsRequest(val projectId: String)
typealias ReadRequestSettingsResponse = ProjectApplicationSettings

data class ApproveApplicationRequest(val requestId: Long)
typealias ApproveApplicationResponse = Unit

data class RejectApplicationRequest(val requestId: Long)
typealias RejectApplicationResponse = Unit

data class CloseApplicationRequest(val requestId: Long)
typealias CloseApplicationResponse = Unit

data class CommentOnApplicationRequest(val requestId: Long, val comment: String)
typealias CommentOnApplicationResponse = Unit

data class DeleteCommentRequest(val commentId: Long)
typealias DeleteCommentResponse = Unit

data class IngoingApplicationsRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias IngoingApplicationsResponse = Page<Application>

data class Comment(val id: Long, val postedBy: String, val postedAt: Long, val comment: String)
data class ApplicationWithComments(val application: Application, val comments: List<Comment>, val approver: Boolean)

data class OutgoingApplicationsRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias OutgoingApplicationsResponse = Page<Application>

typealias SubmitApplicationRequest = CreateApplication
typealias SubmitApplicationResponse = FindByLongId

data class EditApplicationRequest(
    val id: Long,
    val newDocument: String,
    val newResources: List<ResourceRequest>
)
typealias EditApplicationResponse = Unit

enum class ApplicationStatus {
    APPROVED,
    REJECTED,
    CLOSED,
    IN_PROGRESS
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = GrantRecipient.PersonalProject::class, name = GrantRecipient.PERSONAL_TYPE),
    JsonSubTypes.Type(value = GrantRecipient.ExistingProject::class, name = GrantRecipient.EXISTING_PROJECT_TYPE),
    JsonSubTypes.Type(value = GrantRecipient.NewProject::class, name = GrantRecipient.NEW_PROJECT_TYPE)
)
sealed class GrantRecipient {
    class PersonalProject(val username: String) : GrantRecipient()
    class ExistingProject(val projectId: String) : GrantRecipient()
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

data class ResourceRequest(
    val productCategory: String,
    val productProvider: String,
    val creditsRequested: Long?,
    val quotaRequested: Long?
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

data class CreateApplication(
    val resourcesOwnedBy: String, // Project ID of the project owning the resources
    val grantRecipient: GrantRecipient,
    val document: String,
    val requestedResources: List<ResourceRequest> // This is _always_ additive to existing resources
)

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
    val updatedAt: Long
)

data class ViewApplicationRequest(val id: Long)
typealias ViewApplicationResponse = ApplicationWithComments

data class SetEnabledStatusRequest(val projectId: String, val enabledStatus: Boolean)
typealias SetEnabledStatusResponse = Unit

data class IsEnabledRequest(val projectId: String)
data class IsEnabledResponse(val enabled: Boolean)

data class BrowseProjectsRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias BrowseProjectsResponse = Page<ProjectWithTitle>
data class ProjectWithTitle(val projectId: String, val title: String)

object Grants : CallDescriptionContainer("grant") {
    val baseContext = "/api/grant"

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
                }
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
                    +boundTo(OutgoingApplicationsRequest::page)
                }
            }
        }

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

    // This needs to be last
    val viewApplication = call<ViewApplicationRequest, ViewApplicationResponse, CommonErrorMessage>("viewApplication") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +boundTo(ViewApplicationRequest::id)
            }
        }
    }
}

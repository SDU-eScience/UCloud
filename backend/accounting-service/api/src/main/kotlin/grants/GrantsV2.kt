package dk.sdu.cloud.grant.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductCategory
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

object GrantsV2 : CallDescriptionContainer("grants.v2") {
    const val baseContext = "/api/grants/v2"

    init {
        title = "Grant Applications"
        description = """
            Grants provide a way for users of UCloud to apply for resources.

            In order for any user to use UCloud they must have credits. Credits are required for use of any compute or 
            storage. There are only two ways of receiving any credits, either through an admin directly granting you the 
            credits or by receiving them from a project.

            Grants acts as a more user-friendly gateway to receiving resources from a project. Every
            `GrantApplication` goes through the following steps:

            1. User submits application to relevant project
            2. All grant givers must review the application
               - User and reviewer can comment on the application
               - User and reviewer can perform edits to the application
            3. Reviewer either approve or reject
            4. If the `GrantApplication` was approved then resources are granted to the recipient
        """.trimIndent()
    }

    // Core workflow and CRUD
    // =================================================================================================================
    val browse = Browse.call
    val retrieve = Retrieve.call
    val submitRevision = SubmitRevision.call
    val updateState = UpdateState.call
    val transfer = Transfer.call
    val retrieveGrantGivers = RetrieveGrantGivers.call

    object Browse {
        @Serializable
        data class Request(
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,

            val filter: GrantApplicationFilter = GrantApplicationFilter.ACTIVE,
            val includeIngoingApplications: Boolean = false,
            val includeOutgoingApplications: Boolean = false,
        ) : WithPaginationRequestV2

        val call = call(
            "browse",
            Request.serializer(),
            PageV2.serializer(GrantApplication.serializer()),
            CommonErrorMessage.serializer(),
            handler = {
                httpBrowse(baseContext)
            }
        )
    }

    object Retrieve {
        val call = call(
            "retrieve",
            FindByStringId.serializer(),
            GrantApplication.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext)
            }
        )
    }

    object SubmitRevision {
        @Serializable
        data class Request(
            val revision: GrantApplication.Document,
            val comment: String,
            val applicationId: String? = null,
        )

        val call = call(
            "submitRevision",
            Request.serializer(),
            FindByStringId.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "submitRevision")
            }
        )
    }

    object UpdateState {
        @Serializable
        data class Request(
            val applicationId: String,
            val newState: GrantApplication.State,
        )

        val call = call(
            "updateState",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateState")
            }
        )
    }

    object Transfer {
        @Serializable
        data class Request(
            val applicationId: String,
            val target: String,
            val comment: String,
            // NOTE(Dan): Source is implicit from project header
        )

        val call = call(
            "transfer",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "transfer")
            }
        )
    }

    object RetrieveGrantGivers {
        @Serializable
        sealed class Request {
            @SerialName("PersonalWorkspace")
            @Serializable
            class PersonalWorkspace : Request()

            @SerialName("NewProject")
            @Serializable
            class NewProject(val title: String) : Request()

            @SerialName("ExistingProject")
            @Serializable
            class ExistingProject(val id: String) : Request()

            @SerialName("ExistingApplication")
            @Serializable
            class ExistingApplication(val id: String) : Request()
        }

        @Serializable
        data class Response(
            val grantGivers: List<GrantGiver>,
        )

        val call = call(
            "retrieveGrantGivers",
            Request.serializer(),
            Response.serializer(),
            CommonErrorMessage.serializer(),
            handler = { httpUpdate(baseContext, "retrieveGrantGivers") }
        )
    }

    // Comments
    // =================================================================================================================
    val postComment = PostComment.call
    val deleteComment = DeleteComment.call

    object PostComment {
        @Serializable
        data class Request(
            val applicationId: String,
            val comment: String,
        )

        val call = call(
            "postComment",
            Request.serializer(),
            FindByStringId.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "postComment")
            }
        )
    }

    object DeleteComment {
        @Serializable
        data class Request(
            val applicationId: String,
            val commentId: String,
        )

        val call = call(
            "deleteComment",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "deleteComment")
            }
        )
    }

    // Request settings
    // =================================================================================================================
    val updateRequestSettings = UpdateRequestSettings.call
    val retrieveRequestSettings = RetrieveRequestSettings.call
    val uploadLogo = UploadLogo.call
    val retrieveLogo = RetrieveLogo.call

    object UpdateRequestSettings {
        val call = call(
            "updateRequestSettings",
            GrantRequestSettings.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "updateRequestSettings", roles = Roles.END_USER + Role.SERVICE)
            }
        )
    }

    object RetrieveRequestSettings {
        val call = call(
            "retrieveRequestSettings",
            Unit.serializer(), // Project fetched from header
            GrantRequestSettings.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "requestSettings")
            }
        )
    }

    object UploadLogo {
        val call = call(
            "uploadLogo",
            Unit.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpUpdate(baseContext, "uploadLogo")
            }
        )
    }

    object RetrieveLogo {
        @Serializable
        data class Request(
            val projectId: String
        )

        val call = call(
            "retrieveLogo",
            Request.serializer(),
            Unit.serializer(),
            CommonErrorMessage.serializer(),
            handler = {
                httpRetrieve(baseContext, "logo", roles = Roles.PUBLIC)
            }
        )
    }
}

@Serializable
data class GrantRequestSettings(
    val enabled: Boolean,
    val description: String,
    val allowRequestsFrom: List<UserCriteria>,
    val excludeRequestsFrom: List<UserCriteria>,
    val templates: Templates,
)

@Serializable
data class GrantGiver(
    val id: String,
    val title: String,
    val description: String,
    val templates: Templates,
    val categories: List<ProductCategory>
)

@Serializable
@UCloudApiDoc(
    """
        Describes some criteria which match a user
        
        This is used in conjunction with actions that require authorization.
    """
)

@UCloudApiOwnedBy(GrantsV2::class)
@UCloudApiStable
sealed class UserCriteria {
    @Serializable
    @SerialName(ANYONE_TYPE)
    @UCloudApiDoc("Matches any user")
    @UCloudApiStable
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
    @SerialName(EMAIL_TYPE)
    @UCloudApiStable
    data class EmailDomain(val domain: String) : UserCriteria()

    @UCloudApiDoc(
        """
           Matches any user with an organization matching `org`
           
           The organization is currently derived from the information we receive from WAYF.
        """
    )
    @Serializable
    @SerialName(WAYF_TYPE)
    @UCloudApiStable
    data class WayfOrganization(val org: String) : UserCriteria()

    val type: String
        get() = when (this) {
            is Anyone -> ANYONE_TYPE
            is EmailDomain -> EMAIL_TYPE
            is WayfOrganization -> WAYF_TYPE
        }

    val id: String?
        get() = when (this) {
            is Anyone -> null
            is EmailDomain -> domain
            is WayfOrganization -> org
        }

    companion object {
        const val ANYONE_TYPE = "anyone"
        const val EMAIL_TYPE = "email"
        const val WAYF_TYPE = "wayf"
    }
}

@Serializable
sealed class Templates {
    @Serializable
    @SerialName("plain_text")
    data class PlainText(
        @UCloudApiDoc("The template provided for new grant applications when the grant requester is a personal project")
        val personalProject: String,

        @UCloudApiDoc("The template provided for new grant applications when the grant requester is a new project")
        val newProject: String,

        @UCloudApiDoc("The template provided for new grant applications when the grant requester is an existing project")
        val existingProject: String
    ) : Templates()
}

@Serializable
@UCloudApiStable
enum class GrantApplicationFilter {
    SHOW_ALL,
    ACTIVE,
    INACTIVE
}

@Serializable
@UCloudApiStable
data class GrantApplication(
    @UCloudApiDoc(
        """
        A unique identifier representing a GrantApplication
    
        The ID is used for all requests which manipulate the application. The ID is issued by UCloud/Core when the
        initial revision is submitted. The ID is never re-used by the system, even if a previous version has been
        closed.
    """
    )
    val id: String,

    @UCloudApiDoc("Username of the user who originially submitted the application")
    val createdBy: String,
    @UCloudApiDoc("Timestamp representing when the application was originially submitted")
    val createdAt: Long,
    @UCloudApiDoc("Timestamp representing when the application was last updated")
    val updatedAt: Long,

    @UCloudApiDoc("Information about the current revision")
    val currentRevision: Revision,

    @UCloudApiDoc("Status information about the application in its entirety")
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
                A reference used for out-of-band bookkeeping

                Updateable by: Grant givers
                Immutable after creation: No
            """
        )
        val referenceIds: List<String>? = null,

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
        data class PlainText(override val text: String) : Form(), WithText

        @Serializable
        @SerialName("grant_giver_initiated")
        data class GrantGiverInitiated(override val text: String, val subAllocator: Boolean = false) : Form(), WithText

        interface WithText {
            val text: String
        }
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
    }

    @Serializable
    @SerialName("allocation_request")
    @UCloudApiOwnedBy(GrantsV2::class)
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

package dk.sdu.cloud.accounting.api.projects

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.GrantApplication
import dk.sdu.cloud.grant.api.Grants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiDoc(
    """
        Describes some criteria which match a user
        
        This is used in conjunction with actions that require authorization.
    """
)
@UCloudApiOwnedBy(Grants::class)
@UCloudApiStable
sealed class UserCriteria {
    @Serializable
    @SerialName(UserCriteria.ANYONE_TYPE)
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
    @SerialName(UserCriteria.EMAIL_TYPE)
    @UCloudApiStable
    data class EmailDomain(val domain: String) : UserCriteria()

    @UCloudApiDoc(
        """
           Matches any user with an organization matching `org`
           
           The organization is currently derived from the information we receive from WAYF.
        """
    )
    @Serializable
    @SerialName(UserCriteria.WAYF_TYPE)
    @UCloudApiStable
    data class WayfOrganization(val org: String) : UserCriteria()

    companion object {
        const val ANYONE_TYPE = "anyone"
        const val EMAIL_TYPE = "email"
        const val WAYF_TYPE = "wayf"
    }
}

@UCloudApiDoc(
    """
        Settings which control if an Application should be automatically approved
         
        The `Application` will be automatically approved if the all of the following is true:
        - The requesting user matches any of the criteria in `from`
        - The user has only requested resources (`Application.requestedResources`) which are present in `maxResources`
        - None of the resource requests exceed the numbers specified in `maxResources`
    """
)
@UCloudApiInternal(InternalLevel.STABLE)
@Serializable
data class AutomaticApprovalSettings(
    val from: List<UserCriteria>,
    val maxResources: List<GrantApplication.AllocationRequest>
)

@UCloudApiDoc(
    """
        Settings for grant Applications
         
        A user will be allowed to apply for grants to this project if they match any of the criteria listed in
        `allowRequestsFrom`.
    """
)
@Serializable
@UCloudApiInternal(InternalLevel.STABLE)
data class ProjectApplicationSettings(
    val projectId: String,
    val allowRequestsFrom: List<UserCriteria>,
    val excludeRequestsFrom: List<UserCriteria>
)

typealias UploadRequestSettingsRequest = ProjectApplicationSettings
typealias UploadRequestSettingsResponse = Unit

@Serializable
@UCloudApiInternal(InternalLevel.STABLE)
data class RetrieveRequestSettingsRequest(val projectId: String)
typealias RetrieveRequestSettingsResponse = ProjectApplicationSettings

@UCloudApiInternal(InternalLevel.STABLE)
object GrantSettings : CallDescriptionContainer("grant.settings") {
    val baseContext = "/api/grant/settings"

    init {
        title = "Project Grant Settings"
        description = """
Grant Settings contain settings that a project can set in respect to the grant applications they receive.

This include settings for auto approval of received grant applications, settings specifying who can apply
to the project and who should be excluded e.g. students.

${ApiConventions.nonConformingApiWarning}
""".trimIndent()
    }

    val uploadRequestSettings =
        call(
            "uploadRequestSettings",
            BulkRequest.serializer(UploadRequestSettingsRequest.serializer()),
            UploadRequestSettingsResponse.serializer(),
            CommonErrorMessage.serializer()
        ) {
            httpUpdate(baseContext, "upload")

            documentation {
                summary = "Uploads `ProjectApplicationSettings` to be associated with a project. The project must be " +
                        "enabled."
            }
        }

    val retrieveRequestSettings = call(
        "retrieveRequestSettings",
        RetrieveRequestSettingsRequest.serializer(),
        RetrieveRequestSettingsResponse.serializer(),
        CommonErrorMessage.serializer(),
        handler = {
            httpRetrieve(baseContext)

            documentation {
                summary = "Retrieves `ProjectApplicationSettings` associated with the project."
            }
        }
    )
}

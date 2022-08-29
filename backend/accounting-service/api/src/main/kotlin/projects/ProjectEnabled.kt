package dk.sdu.cloud.accounting.api.projects

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.grant.api.Grants
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer


@Serializable
data class SetEnabledStatusRequest(val projectId: String, val enabledStatus: Boolean)
typealias SetEnabledStatusResponse = Unit

@Serializable
data class IsEnabledRequest(val projectId: String)
@Serializable
data class IsEnabledResponse(val enabled: Boolean)


object ProjectEnabled : CallDescriptionContainer("projectEnabled") {
    val baseContext = "/api/project/enabled"

    init {
        title = "Project Enabled"
        description = """
            Projects Enabled status is used for enabling projects to be receivable to grant applications.
            Once a project is enabled it will not automatically be shown to every user, but it will have to specify 
            from where or who it wishes to receive grant applications from using the [ProjectApplicationSettings].
            
            ${ApiConventions.nonConformingApiWarning}
        """.trimIndent()
    }

    val setEnabledStatus =
        call(
            "setEnabledStatus",
            BulkRequest.serializer(SetEnabledStatusRequest.serializer()),
            SetEnabledStatusResponse.serializer(),
            CommonErrorMessage.serializer()
        ) {
            httpUpdate(
                Grants.baseContext,
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

    val isEnabled = call(
        "isEnabled",
        IsEnabledRequest.serializer(),
        IsEnabledResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpRetrieve(
            baseContext
        )

        documentation {
            summary =
                "If this returns true then the project (as specified by [IsEnabledRequest.projectId]) can receive " +
                    "grant [Application]s."
        }
    }
}

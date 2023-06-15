package dk.sdu.cloud.auth.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@UCloudApiInternal(InternalLevel.BETA)
@Serializable
data class Registration(
    val sessionId: String,
    val firstNames: String?,
    val lastName: String?,
    val email: String?,
)

object Registrations : CallDescriptionContainer("auth.registration") {
    private const val baseContext = "/auth/registration"

    val retrieve = call(
        name = "retrieve",
        requestType = FindByStringId.serializer(),
        successType = Registration.serializer(),
        errorType = CommonErrorMessage.serializer(),
        handler = {
            audit(Unit.serializer())
            httpRetrieve(baseContext, roles = Roles.PUBLIC)
      },
    )

    val complete = call(
        name = "complete",
        requestType = Unit.serializer(), // Registration through form
        successType = Unit.serializer(),
        errorType = CommonErrorMessage.serializer(),
        handler = {
            audit(Unit.serializer())
            httpUpdate(baseContext, "complete", roles = Roles.PUBLIC)
        },
    )

    val verifyEmail = call(
        name = "verifyEmail",
        requestType = FindByStringId.serializer(),
        successType = Unit.serializer(),
        errorType = CommonErrorMessage.serializer(),
        handler = {
            http {
                audit(Unit.serializer())

                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"verifyEmail"
                }

                params { +boundTo(FindByStringId::id) }
            }
        }
    )

    val resendVerificationEmail = call(
        name = "resendVerificationEmail",
        requestType = FindByStringId.serializer(),
        successType = Unit.serializer(),
        errorType = CommonErrorMessage.serializer(),
        handler = {
            http {
                audit(Unit.serializer())

                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"reverify"
                }

                params { +boundTo(FindByStringId::id) }
            }
        }
    )
}
